package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "skills")
@Getter
@Setter
public class Skill extends BaseEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private UserAccount owner;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private Category category;

  @Column(nullable = false)
  private boolean active = true;
}
