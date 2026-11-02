package com.credlearn.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "conversation_id")
  private Conversation conversation;

  @ManyToOne(optional = false)
  @JoinColumn(name = "sender_id")
  private UserAccount sender;

  @Column(nullable = false, length = 36)
  private String clientMessageId;

  @Column(nullable = false, length = 2000)
  private String messageContent;

  @Column(nullable = false)
  private Instant sentAt;

  private Instant readAt;
}
