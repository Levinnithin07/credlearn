package com.credlearn.service;

import com.credlearn.dto.Forms;
import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SkillService {
  private final SkillRepository skills;
  private final UserRepository users;
  private final InterestRepository interests;

  public Skill get(Long id) {
    return skills.findById(id).orElseThrow(() -> new RuleViolation("Skill not found."));
  }

  public Skill owned(Long id, Long user) {
    var s = get(id);
    if (!s.getOwner().getId().equals(user))
      throw new AccessDeniedException("This skill belongs to another student.");
    return s;
  }

  public Skill save(Long id, Long user, Forms.SkillForm f) {
    var s = id == null ? new Skill() : owned(id, user);
    if (id == null) s.setOwner(users.findById(user).orElseThrow());
    s.setTitle(f.getTitle().trim());
    s.setDescription(f.getDescription().trim());
    s.setCategory(f.getCategory());
    return skills.save(s);
  }

  public void toggle(Long id, Long user) {
    var s = owned(id, user);
    s.setActive(!s.isActive());
  }

  public void addInterest(Long user, Forms.InterestForm f) {
    String name = f.getSkillName().trim();
    if (interests.existsByUserIdAndSkillNameIgnoreCase(user, name))
      throw new RuleViolation("This interest is already in your profile.");
    var i = new LearningInterest();
    i.setUser(users.findById(user).orElseThrow());
    i.setSkillName(name);
    i.setCategory(f.getCategory());
    interests.save(i);
  }

  public void removeInterest(Long id, Long user) {
    var i = interests.findById(id).orElseThrow();
    if (!i.getUser().getId().equals(user))
      throw new AccessDeniedException("This interest belongs to another student.");
    interests.delete(i);
  }
}
