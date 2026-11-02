package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserAccount extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String fullName;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 100)
  private String department;

  @Column(nullable = false)
  private int yearOfStudy;

  @Column(nullable = false, length = 1000)
  private String bio = "";

  @Column(nullable = false)
  private boolean compactView;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountRole role = AccountRole.STUDENT;
}
