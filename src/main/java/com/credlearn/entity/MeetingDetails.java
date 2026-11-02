package com.credlearn.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "meeting_details")
@Getter
@Setter
public class MeetingDetails extends BaseEntity {
  @OneToOne(optional = false)
  @JoinColumn(name = "session_id", unique = true)
  private LearningSession session;

  @Column(nullable = false, length = 64, unique = true)
  private String externalEventId;

  @Column(nullable = false, length = 64)
  private String conferenceRequestId;

  @Column(nullable = false, length = 255)
  private String googleSubject;

  @Column(nullable = false)
  private Long createdBy;

  @Column(length = 500)
  private String meetingUrl;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private Instant scheduledStart;

  @Column(nullable = false)
  private Instant scheduledEnd;

  @Column(nullable = false, length = 400)
  private String lastError = "";

  private Instant leaseUntil;
}
