package com.credlearn.repository;

import com.credlearn.entity.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ConfirmationRepository extends JpaRepository<Confirmation, Long> {
  boolean existsBySessionIdAndUserIdAndVoidedAtIsNull(Long sessionId, Long userId);

  Optional<Confirmation> findBySessionIdAndUserId(Long sessionId, Long userId);

  long countBySessionIdAndVoidedAtIsNull(Long sessionId);

  long countBySessionId(Long sessionId);

  List<Confirmation> findBySessionId(Long sessionId);
}
