package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "credit_transactions")
@Getter
@Setter
public class CreditTransaction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "session_id", unique = true)
  private LearningSession session;

  @ManyToOne
  @JoinColumn(name = "sender_id")
  private UserAccount sender;

  @ManyToOne(optional = false)
  @JoinColumn(name = "receiver_id")
  private UserAccount receiver;

  @Column(nullable = false, precision = 12, scale = 2)
  private java.math.BigDecimal amount;

  @Column(nullable = false, length = 30)
  private String transactionType;

  @Column(nullable = false)
  private java.time.Instant createdAt;

  @Column(unique = true)
  private Long starterUserId;
}
