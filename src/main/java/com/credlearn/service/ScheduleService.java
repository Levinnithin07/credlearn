package com.credlearn.service;

import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ScheduleService {
  private final SessionRepository sessions;
  private final WalletRepository wallets;
  private final MeetingRepository meetings;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Value("${credlearn.zone}")
  private String zone;

  public void propose(Long id, Long user, LocalDateTime start, int duration) {
    var s = locked(id, user);
    editable(s);
    if (s.getScheduleProposedBy() != null)
      throw new RuleViolation("Resolve the existing schedule proposal first.");
    if (start == null || duration < 30 || duration > 240 || duration % 15 != 0)
      throw new RuleViolation("Choose a future time and 30–240 minutes in 15-minute increments.");
    var instant = start.atZone(ZoneId.of(zone)).toInstant();
    if (!instant.isAfter(clock.instant())) throw new RuleViolation("Choose a future session time.");
    s.setProposedStartsAt(instant);
    s.setProposedDurationMinutes(duration);
    s.setScheduleProposedBy(user);
    s.setScheduleRevision(s.getScheduleRevision() + 1);
    events.publishEvent(new SessionChanged(id));
  }

  public void approve(Long id, Long user, long revision) {
    var s = locked(id, user);
    editable(s);
    proposal(s, revision);
    if (s.getScheduleProposedBy().equals(user))
      throw new RuleViolation("The other participant must approve your proposal.");
    if (!s.getProposedStartsAt().isAfter(clock.instant()))
      throw new RuleViolation("This proposal has expired. Dismiss it and propose a new time.");
    Map<Long, Wallet> accounts = new HashMap<>();
    for (var who :
        List.of(s.getRequester().getId(), s.getProvider().getId()).stream().sorted().toList())
      accounts.put(who, wallets.lock(who).orElseThrow());
    var learner = accounts.get(s.getRequester().getId());
    var next =
        BigDecimal.valueOf(s.getProposedDurationMinutes())
            .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.UNNECESSARY);
    if (learner.getAvailable().add(s.getCredits()).compareTo(next) < 0)
      throw new RuleViolation("The learner needs more available credits for this duration.");
    var end = s.getProposedStartsAt().plusSeconds(s.getProposedDurationMinutes() * 60L);
    if (sessions.activeForParticipants(new ArrayList<>(accounts.keySet())).stream()
        .anyMatch(
            other ->
                !other.getId().equals(id)
                    && other.getStartsAt().isBefore(end)
                    && other.getEndsAt().isAfter(s.getProposedStartsAt())))
      throw new RuleViolation("One participant already has a session at the proposed time.");
    learner.setReserved(learner.getReserved().subtract(s.getCredits()).add(next));
    s.setStartsAt(s.getProposedStartsAt());
    s.setDurationMinutes(s.getProposedDurationMinutes());
    clear(s);
    events.publishEvent(new SessionChanged(id));
  }

  public void dismiss(Long id, Long user, long revision) {
    var s = locked(id, user);
    proposal(s, revision);
    clear(s);
    events.publishEvent(new SessionChanged(id));
  }

  private void clear(LearningSession s) {
    s.setScheduleProposedBy(null);
    s.setProposedStartsAt(null);
    s.setProposedDurationMinutes(null);
    s.setScheduleRevision(s.getScheduleRevision() + 1);
  }

  private void proposal(LearningSession s, long revision) {
    if (s.getScheduleProposedBy() == null || s.getScheduleRevision() != revision)
      throw new RuleViolation("This proposal changed. Reload the session.");
  }

  private void editable(LearningSession s) {
    if (s.getStatus() != SessionStatus.ACCEPTED || !s.getStartsAt().isAfter(clock.instant()))
      throw new RuleViolation("Only a future accepted session can be rescheduled.");
    if (meetings.existsBySessionId(s.getId()))
      throw new RuleViolation(
          "The meeting schedule is locked once Google creation starts. Cancel and request a new"
              + " session to change it.");
  }

  private LearningSession locked(Long id, Long user) {
    var s = sessions.lock(id).orElseThrow(() -> new RuleViolation("Session not found."));
    if (!s.participant(user))
      throw new AccessDeniedException("Only participants can change the schedule.");
    return s;
  }
}
