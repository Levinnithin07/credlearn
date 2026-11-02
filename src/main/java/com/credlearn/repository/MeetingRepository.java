package com.credlearn.repository;

import com.credlearn.entity.MeetingDetails;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<MeetingDetails, Long> {
  Optional<MeetingDetails> findBySessionId(Long sessionId);

  boolean existsBySessionId(Long sessionId);
}
