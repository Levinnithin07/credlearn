package com.credlearn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "credit_wallets")
@Getter
@Setter
public class Wallet extends BaseEntity {

  @OneToOne(optional = false)
  @JoinColumn(name = "user_id", unique = true)
  private UserAccount user;

  @Column(nullable = false, precision = 12, scale = 2)
  private java.math.BigDecimal balance = java.math.BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private java.math.BigDecimal reserved = java.math.BigDecimal.ZERO;

  @Version private long version;

  public java.math.BigDecimal getAvailable() {
    return balance.subtract(reserved);
  }
}
