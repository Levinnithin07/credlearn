package com.credlearn.service;

import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import java.time.*;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class MeetingService {
  private final SessionRepository sessions;
  private final MeetingRepository meetings;
  private final GoogleCalendarGateway google;
  private final TransactionTemplate tx;
  private final Clock clock;

  public MeetingService(
      SessionRepository sessions,
      MeetingRepository meetings,
      GoogleCalendarGateway google,
      PlatformTransactionManager manager,
      Clock clock) {
    this.sessions = sessions;
    this.meetings = meetings;
    this.google = google;
    this.tx = new TransactionTemplate(manager);
    this.clock = clock;
  }

  public MeetingDetails view(Long id, Long user) {
    return tx.execute(
        status -> {
          authorized(id, user, false);
          return meetings.findBySessionId(id).orElse(null);
        });
  }

  public void eligible(Long id, Long user) {
    tx.executeWithoutResult(status -> check(authorized(id, user, false)));
  }

  public MeetingDetails create(Long id, Long user, GoogleConnection.Tokens credentials) {
    if (!credentials.user().equals(user))
      throw new AccessDeniedException("Google connection belongs to another student.");
    // Commit the stable event ID before contacting Google, including when a response is lost.
    var intent =
        tx.execute(
            status -> {
              var s = authorized(id, user, true);
              check(s);
              var m = meetings.findBySessionId(id).orElse(null);
              if (m != null && "READY".equals(m.getStatus())) return m;
              if (m == null) {
                m = new MeetingDetails();
                m.setSession(s);
                m.setCreatedBy(user);
                m.setGoogleSubject(credentials.subject());
                m.setExternalEventId(UUID.randomUUID().toString().replace("-", ""));
                m.setConferenceRequestId(UUID.randomUUID().toString());
                m.setScheduledStart(s.getStartsAt());
                m.setScheduledEnd(s.getEndsAt());
              }
              if (!m.getCreatedBy().equals(user)
                  || !m.getGoogleSubject().equals(credentials.subject()))
                throw new RuleViolation(
                    "The original organizer must reconnect the same Google account to finish"
                        + " creating this meeting.");
              if (m.getLeaseUntil() != null && m.getLeaseUntil().isAfter(clock.instant()))
                throw new RuleViolation(
                    "Meeting creation is already in progress. Wait a moment and reload.");
              m.setStatus("CREATING");
              m.setLeaseUntil(clock.instant().plusSeconds(90));
              m.setLastError("");
              return meetings.saveAndFlush(m);
            });
    if ("READY".equals(intent.getStatus())) return intent;
    GoogleCalendarGateway.Result result;
    try {
      result =
          google.ensure(
              credentials.access(),
              intent.getExternalEventId(),
              intent.getConferenceRequestId(),
              intent.getSession().getSkill().getTitle(),
              intent.getScheduledStart(),
              intent.getScheduledEnd());
    } catch (HttpClientErrorException.Unauthorized ex) {
      result =
          new GoogleCalendarGateway.Result(
              "ERROR",
              null,
              "Google permission expired or was revoked. Disconnect and reconnect the same Google"
                  + " account.");
    } catch (Exception ex) {
      result =
          new GoogleCalendarGateway.Result(
              "ERROR",
              null,
              "Google did not confirm meeting creation. Check permissions or connectivity, then"
                  + " retry with the same Google account. The existing event identifier will be"
                  + " reused.");
    }
    var outcome = result;
    return tx.execute(
        status -> {
          authorized(id, user, true);
          var m = meetings.findBySessionId(id).orElseThrow();
          m.setStatus(outcome.status());
          m.setMeetingUrl(outcome.url());
          m.setLastError(outcome.error());
          m.setLeaseUntil(null);
          return meetings.save(m);
        });
  }

  private LearningSession authorized(Long id, Long user, boolean lock) {
    var s =
        (lock ? sessions.lock(id) : sessions.findById(id))
            .orElseThrow(() -> new RuleViolation("Session not found."));
    if (!s.participant(user))
      throw new AccessDeniedException("Only session participants can access meetings.");
    return s;
  }

  private void check(LearningSession s) {
    if (s.getStatus() != SessionStatus.ACCEPTED || !s.getStartsAt().isAfter(clock.instant()))
      throw new RuleViolation("Meetings can be created only for future accepted sessions.");
    if (s.getScheduleProposedBy() != null)
      throw new RuleViolation("Agree on the pending schedule proposal before creating a meeting.");
  }
}
