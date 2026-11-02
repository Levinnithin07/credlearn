package com.credlearn.repository;

import com.credlearn.entity.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface InterestRepository extends JpaRepository<LearningInterest, Long> {
  List<LearningInterest> findByUserIdOrderBySkillName(Long userId);

  boolean existsByUserIdAndSkillNameIgnoreCase(Long userId, String skillName);
}
