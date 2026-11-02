package com.credlearn.repository;

import com.credlearn.entity.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<CreditTransaction, Long> {
  boolean existsBySessionId(Long sessionId);

  @Query(
      "select t from CreditTransaction t where t.sender.id=:id or t.receiver.id=:id order by"
          + " t.createdAt desc")
  List<CreditTransaction> forUser(@Param("id") Long id);
}
