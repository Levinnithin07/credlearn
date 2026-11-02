package com.credlearn.repository;

import com.credlearn.entity.Conversation;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
  Optional<Conversation> findBySessionId(Long sessionId);

  boolean existsBySessionId(Long sessionId);

  @Query(
      "select c from Conversation c where c.session.requester.id=:user or"
          + " c.session.provider.id=:user order by c.updatedAt desc")
  List<Conversation> forUser(Long user);
}
