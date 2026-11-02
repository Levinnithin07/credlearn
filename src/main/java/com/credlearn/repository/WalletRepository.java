package com.credlearn.repository;

import com.credlearn.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
  Optional<Wallet> findByUserId(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.user.id=:id")
  Optional<Wallet> lock(@Param("id") Long id);
}
