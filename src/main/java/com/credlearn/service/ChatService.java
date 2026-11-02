package com.credlearn.service;

import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ChatService {
  private final SessionRepository sessions;
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final UserRepository users;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public record Message(
      Long id,
      Long senderId,
      String sender,
      String content,
      Instant sentAt,
      Instant readAt,
      String clientMessageId) {}

  public record History(List<Message> messages, boolean writable, String status) {}

  public record ThreadSummary(
      Long sessionId, String skill, String peer, String status, long unread, boolean writable) {}

  public Conversation access(Long session, Long user) {
    var s = sessions.findById(session).orElseThrow(() -> new RuleViolation("Session not found."));
    if (!s.participant(user)) throw new AccessDeniedException("This conversation is private.");
    if (s.getStatus() == SessionStatus.PENDING || s.getStatus() == SessionStatus.DECLINED)
      throw new RuleViolation("Chat opens only after the teacher accepts the request.");
    return conversations
        .findBySessionId(session)
        .orElseThrow(() -> new RuleViolation("This session has no conversation."));
  }

  public boolean writable(LearningSession s) {
    return s.getStatus() == SessionStatus.ACCEPTED || s.getStatus() == SessionStatus.DISPUTED;
  }

  public History history(Long session, Long user, long after, long before) {
    var c = access(session, user);
    List<ChatMessage> rows;
    if (after > 0)
      rows =
          messages.findByConversationIdAndIdGreaterThanOrderByIdAsc(
              c.getId(), after, PageRequest.of(0, 50));
    else {
      rows =
          new ArrayList<>(
              messages.findByConversationIdAndIdLessThanOrderByIdDesc(
                  c.getId(), before > 0 ? before : Long.MAX_VALUE, PageRequest.of(0, 50)));
      Collections.reverse(rows);
    }
    return new History(
        rows.stream().map(this::dto).toList(),
        writable(c.getSession()),
        c.getSession().getStatus().name());
  }

  public Message send(Long session, Long user, String text, String clientId) {
    var s = sessions.lock(session).orElseThrow(() -> new RuleViolation("Session not found."));
    var c = access(session, user);
    if (!writable(s))
      throw new RuleViolation("This conversation is read-only after completion or cancellation.");
    if (text == null
        || text.isBlank()
        || text.length() > 2000
        || text.codePoints()
            .anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\t' && ch != '\r'))
      throw new RuleViolation("Send 1–2,000 characters of plain text.");
    try {
      UUID.fromString(clientId);
    } catch (Exception e) {
      throw new RuleViolation("Invalid message identifier. Reload the chat.");
    }
    var existing =
        messages.findByConversationIdAndSenderIdAndClientMessageId(c.getId(), user, clientId);
    if (existing.isPresent()) return dto(existing.get());
    if (messages.countByConversationIdAndSenderIdAndSentAtAfter(
            c.getId(), user, clock.instant().minusSeconds(60))
        >= 20) throw new RuleViolation("Please wait a moment before sending more messages.");
    var m = new ChatMessage();
    m.setConversation(c);
    m.setSender(users.findById(user).orElseThrow());
    m.setMessageContent(text.strip());
    m.setClientMessageId(clientId);
    m.setSentAt(clock.instant());
    messages.saveAndFlush(m);
    events.publishEvent(new SessionChanged(session));
    return dto(m);
  }

  public void read(Long session, Long user, long through) {
    var c = access(session, user);
    var last =
        messages.findById(through).orElseThrow(() -> new RuleViolation("Message not found."));
    if (!last.getConversation().getId().equals(c.getId()))
      throw new AccessDeniedException("Message is not in this conversation.");
    messages.markRead(c.getId(), user, through, clock.instant());
  }

  public List<ThreadSummary> threads(Long user) {
    return conversations.forUser(user).stream()
        .map(
            c -> {
              var s = c.getSession();
              return new ThreadSummary(
                  s.getId(),
                  s.getSkill().getTitle(),
                  s.getRequester().getId().equals(user)
                      ? s.getProvider().getFullName()
                      : s.getRequester().getFullName(),
                  s.getStatus().name(),
                  messages.countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), user),
                  writable(s));
            })
        .toList();
  }

  private Message dto(ChatMessage m) {
    return new Message(
        m.getId(),
        m.getSender().getId(),
        m.getSender().getFullName(),
        m.getMessageContent(),
        m.getSentAt(),
        m.getReadAt(),
        m.getClientMessageId());
  }
}
