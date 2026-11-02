package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "learning_interests")
@Getter
@Setter
public class LearningInterest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private UserAccount user;

  @Column(nullable = false, length = 100)
  private String skillName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private Category category;
}
