package com.credlearn.repository;

import com.credlearn.entity.ChatMessage;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
  Optional<ChatMessage> findByConversationIdAndSenderIdAndClientMessageId(
      Long conversation, Long sender, String clientMessageId);

  List<ChatMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(
      Long conversation, Long after, Pageable page);

  List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(
      Long conversation, Long before, Pageable page);

  long countByConversationIdAndSenderIdAndSentAtAfter(
      Long conversation, Long sender, Instant since);

  long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversation, Long user);

  @Modifying
  @Query(
      "update ChatMessage m set m.readAt=:now where m.conversation.id=:conversation and"
          + " m.sender.id<>:user and m.id<=:through and m.readAt is null")
  int markRead(Long conversation, Long user, Long through, Instant now);
}
