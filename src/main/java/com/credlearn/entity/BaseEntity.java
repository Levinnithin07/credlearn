package com.credlearn.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  protected Long id;

  @Column(nullable = false, updatable = false)
  protected Instant createdAt;

  @Column(nullable = false)
  protected Instant updatedAt;

  @PrePersist
  void created() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
