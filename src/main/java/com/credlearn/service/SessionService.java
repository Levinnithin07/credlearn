package com.credlearn.service;

import com.credlearn.dto.Forms;
import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SessionService {
  private final SessionRepository sessions;
  private final SkillRepository skills;
  private final UserRepository users;
  private final WalletRepository wallets;
  private final ConfirmationRepository confirmations;
  private final TransactionRepository transactions;
  private final Clock clock;
  private final ConversationRepository conversations;
  private final org.springframework.context.ApplicationEventPublisher events;

  @Value("${credlearn.zone}")
  private String zone;

  public LearningSession request(Long skillId, Long learner, Forms.SessionForm f) {
    var skill = skills.findById(skillId).orElseThrow(() -> new RuleViolation("Skill not found."));
    if (!skill.isActive()) throw new RuleViolation("This skill is no longer available.");
    if (skill.getOwner().getId().equals(learner))
      throw new RuleViolation("You cannot request your own skill.");
    if (f.getDurationMinutes() < 30
        || f.getDurationMinutes() > 240
        || f.getDurationMinutes() % 15 != 0)
      throw new RuleViolation("Choose 30–240 minutes in 15-minute increments.");
    var start = f.getStartsAt().atZone(ZoneId.of(zone)).toInstant();
    if (!start.isAfter(clock.instant())) throw new RuleViolation("Choose a future session time.");
    var s = new LearningSession();
    s.setSkill(skill);
    s.setProvider(skill.getOwner());
    s.setRequester(users.findById(learner).orElseThrow());
    s.setStartsAt(start);
    s.setDurationMinutes(f.getDurationMinutes());
    s.setRequesterMessage(f.getMessage().trim());
    return sessions.save(s);
  }

  public LearningSession detail(Long id, Long user) {
    var s = sessions.findById(id).orElseThrow(() -> new RuleViolation("Session not found."));
    participant(s, user);
    return s;
  }

  public void accept(Long id, Long user) {
    var s = locked(id, user);
    if (!s.getProvider().getId().equals(user))
      throw new AccessDeniedException("Only the teacher can accept.");
    require(s, SessionStatus.PENDING);
    if (!s.getStartsAt().isAfter(clock.instant()))
      throw new RuleViolation("This request has expired. Please arrange a new session.");
    if (!s.getSkill().isActive())
      throw new RuleViolation("Reactivate the skill before accepting this request.");
    // Lock both participants in the same order for all reservations and transfers.
    var accounts = lockWallets(s);
    var learner = accounts.get(s.getRequester().getId());
    if (learner.getAvailable().compareTo(s.getCredits()) < 0)
      throw new RuleViolation("The learner does not have enough available credits.");
    boolean overlap =
        sessions
            .activeForParticipants(List.of(s.getRequester().getId(), s.getProvider().getId()))
            .stream()
            .anyMatch(
                other ->
                    other.getStartsAt().isBefore(s.getEndsAt())
                        && other.getEndsAt().isAfter(s.getStartsAt()));
    if (overlap)
      throw new RuleViolation("One participant already has an accepted session at this time.");
    learner.setReserved(learner.getReserved().add(s.getCredits()));
    s.setStatus(SessionStatus.ACCEPTED);
    if (!conversations.existsBySessionId(id)) {
      var conversation = new Conversation();
      conversation.setSession(s);
      conversations.save(conversation);
    }
    events.publishEvent(new SessionChanged(id));
  }

  public void decline(Long id, Long user) {
    var s = locked(id, user);
    if (!s.getProvider().getId().equals(user))
      throw new AccessDeniedException("Only the teacher can decline.");
    require(s, SessionStatus.PENDING);
    s.setStatus(SessionStatus.DECLINED);
  }

  public void cancel(Long id, Long user) {
    var s = locked(id, user);
    if (s.getStatus() == SessionStatus.PENDING) {
      s.setStatus(SessionStatus.CANCELLED);
      return;
    }
    require(s, SessionStatus.ACCEPTED);
    if (!clock.instant().isBefore(s.getStartsAt()))
      throw new RuleViolation("After a session starts, report an issue instead of cancelling.");
    var learner = lockWallets(s).get(s.getRequester().getId());
    learner.setReserved(learner.getReserved().subtract(s.getCredits()));
    s.setStatus(SessionStatus.CANCELLED);
    events.publishEvent(new SessionChanged(id));
  }

  public void reportIssue(Long id, Long user, String issue) {
    var s = locked(id, user);
    require(s, SessionStatus.ACCEPTED);
    if (clock.instant().isBefore(s.getStartsAt()))
      throw new RuleViolation("You can cancel before the session starts.");
    if (issue == null || issue.isBlank() || issue.length() > 1000)
      throw new RuleViolation("Describe the issue in 1–1000 characters.");
    s.setIssue(issue.trim());
    s.setStatus(SessionStatus.DISPUTED);
    // A dispute withdraws previous agreement; both participants must affirm again.
    confirmations.findBySessionId(id).forEach(c -> c.setVoidedAt(clock.instant()));
    events.publishEvent(new SessionChanged(id));
  }

  public void confirm(Long id, Long user) {
    var s = locked(id, user);
    if (s.getStatus() == SessionStatus.COMPLETED) return;
    if (s.getStatus() != SessionStatus.ACCEPTED && s.getStatus() != SessionStatus.DISPUTED)
      throw new RuleViolation("Only an accepted session can be confirmed.");
    if (clock.instant().isBefore(s.getEndsAt()))
      throw new RuleViolation("Confirm after the scheduled session ends.");
    if (confirmations.existsBySessionIdAndUserIdAndVoidedAtIsNull(id, user)) return;
    var c = confirmations.findBySessionIdAndUserId(id, user).orElseGet(Confirmation::new);
    c.setSession(s);
    c.setUser(users.findById(user).orElseThrow());
    c.setConfirmedAt(clock.instant());
    c.setVoidedAt(null);
    confirmations.saveAndFlush(c);
    if (confirmations.countBySessionIdAndVoidedAtIsNull(id) != 2) return;
    if (transactions.existsBySessionId(id))
      throw new RuleViolation("This session was already settled.");
    var accounts = lockWallets(s);
    var learner = accounts.get(s.getRequester().getId());
    var teacher = accounts.get(s.getProvider().getId());
    var amount = s.getCredits();
    if (learner.getBalance().compareTo(amount) < 0 || learner.getReserved().compareTo(amount) < 0)
      throw new RuleViolation("The reservation is inconsistent. No credits were transferred.");
    learner.setReserved(learner.getReserved().subtract(amount));
    learner.setBalance(learner.getBalance().subtract(amount));
    teacher.setBalance(teacher.getBalance().add(amount));
    var tx = new CreditTransaction();
    tx.setSession(s);
    tx.setSender(s.getRequester());
    tx.setReceiver(s.getProvider());
    tx.setAmount(amount);
    tx.setTransactionType("SESSION");
    tx.setCreatedAt(clock.instant());
    transactions.saveAndFlush(tx);
    s.setStatus(SessionStatus.COMPLETED);
    events.publishEvent(new SessionChanged(id));
  }

  private LearningSession locked(Long id, Long user) {
    var s = sessions.lock(id).orElseThrow(() -> new RuleViolation("Session not found."));
    participant(s, user);
    return s;
  }

  private void participant(LearningSession s, Long user) {
    if (!s.participant(user))
      throw new AccessDeniedException("Only the session participants can access it.");
  }

  private void require(LearningSession s, SessionStatus status) {
    if (s.getStatus() != status)
      throw new RuleViolation(
          "This action is not available for a " + s.getStatus().name().toLowerCase() + " session.");
  }

  private Map<Long, Wallet> lockWallets(LearningSession s) {
    var result = new HashMap<Long, Wallet>();
    for (var id :
        List.of(s.getRequester().getId(), s.getProvider().getId()).stream().sorted().toList())
      result.put(id, wallets.lock(id).orElseThrow());
    return result;
  }
}
