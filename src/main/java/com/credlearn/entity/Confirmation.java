package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "session_confirmations")
@Getter
@Setter
public class Confirmation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "session_id")
  private LearningSession session;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private UserAccount user;

  @Column(nullable = false)
  private java.time.Instant confirmedAt;

  private java.time.Instant voidedAt;
}
