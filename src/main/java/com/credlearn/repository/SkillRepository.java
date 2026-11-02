package com.credlearn.repository;

import com.credlearn.entity.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, Long> {
  List<Skill> findByOwnerIdOrderByCreatedAtDesc(Long userId);

  @Query(
      "select s from Skill s where s.active=true and (:category is null or s.category=:category)"
          + " and (lower(s.title) like lower(concat('%',:q,'%')) or lower(s.description) like"
          + " lower(concat('%',:q,'%'))) order by s.createdAt desc")
  Page<Skill> search(@Param("q") String q, @Param("category") Category category, Pageable pageable);
}
