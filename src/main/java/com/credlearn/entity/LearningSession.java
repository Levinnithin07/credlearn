package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "session_requests")
@Getter
@Setter
public class LearningSession extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "requester_id")
  private UserAccount requester;

  @ManyToOne(optional = false)
  @JoinColumn(name = "provider_id")
  private UserAccount provider;

  @ManyToOne(optional = false)
  @JoinColumn(name = "skill_id")
  private Skill skill;

  @Column(nullable = false)
  private java.time.Instant startsAt;

  @Column(nullable = false)
  private int durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SessionStatus status = SessionStatus.PENDING;

  @Column(nullable = false, length = 1000)
  private String requesterMessage = "";

  @Column(nullable = false, length = 1000)
  private String issue = "";

  private java.time.Instant proposedStartsAt;
  private Integer proposedDurationMinutes;
  private Long scheduleProposedBy;

  @Column(nullable = false)
  private long scheduleRevision;

  public java.math.BigDecimal getCredits() {
    return java.math.BigDecimal.valueOf(durationMinutes)
        .divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.UNNECESSARY);
  }

  public java.time.Instant getEndsAt() {
    return startsAt.plusSeconds(durationMinutes * 60L);
  }

  public boolean participant(Long userId) {
    return requester.getId().equals(userId) || provider.getId().equals(userId);
  }
}
