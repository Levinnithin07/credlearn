package com.credlearn.repository;

import com.credlearn.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository("learningSessionRepository")
public interface SessionRepository extends JpaRepository<LearningSession, Long> {
  @Query(
      "select s from LearningSession s where s.requester.id=:id or s.provider.id=:id order by"
          + " s.startsAt desc")
  List<LearningSession> forUser(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from LearningSession s where s.id=:id")
  Optional<LearningSession> lock(@Param("id") Long id);

  @Query(
      "select s from LearningSession s where s.status in"
          + " (com.credlearn.entity.SessionStatus.ACCEPTED,com.credlearn.entity.SessionStatus.DISPUTED)"
          + " and (s.requester.id in :ids or s.provider.id in :ids)")
  List<LearningSession> activeForParticipants(@Param("ids") List<Long> ids);
}
