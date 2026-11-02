package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conversations")
@Getter
@Setter
public class Conversation extends BaseEntity {
  @OneToOne(optional = false)
  @JoinColumn(name = "session_id", unique = true)
  private LearningSession session;
}
