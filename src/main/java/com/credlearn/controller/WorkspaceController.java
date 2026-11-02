package com.credlearn.controller;

import com.credlearn.dto.Forms;
import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import com.credlearn.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class WorkspaceController {
  private final UserService users;
  private final SkillService skillService;
  private final SessionService sessionService;
  private final OverviewService overview;
  private final SkillRepository skills;
  private final InterestRepository interests;
  private final SessionRepository sessions;
  private final ConfirmationRepository confirmations;
  private final Clock clock;
  private final ChatService chats;
  private final MeetingService meetings;
  private final GoogleConnection google;
  private final ConversationRepository conversations;

  private Long uid(Principal p) {
    return users.current(p.getName()).getId();
  }

  private void page(Model m, String section, String title) {
    m.addAttribute("section", section);
    m.addAttribute("pageTitle", title);
  }

  private String saved(RedirectAttributes flash, String path, String message) {
    flash.addFlashAttribute("success", message);
    return "redirect:" + path;
  }

  @GetMapping("/dashboard")
  String dashboard(Principal p, Model m) {
    var id = uid(p);
    page(m, "dashboard", "Your learning, in motion");
    m.addAllAttributes(overview.dashboard(id));
    var threads = chats.threads(id);
    m.addAttribute(
        "chatUnread", threads.stream().mapToLong(ChatService.ThreadSummary::unread).sum());
    m.addAttribute(
        "chatActive", threads.stream().filter(ChatService.ThreadSummary::writable).count());
    m.addAttribute(
        "upcomingMeetings",
        sessions.forUser(id).stream()
            .filter(
                s ->
                    s.getStatus() == SessionStatus.ACCEPTED
                        && s.getEndsAt().isAfter(clock.instant()))
            .map(s -> meetings.view(s.getId(), id))
            .filter(Objects::nonNull)
            .filter(mt -> "READY".equals(mt.getStatus()))
            .toList());
    m.addAttribute(
        "suggested",
        skills.search("", null, PageRequest.of(0, 6)).getContent().stream()
            .filter(s -> !s.getOwner().getId().equals(id))
            .limit(3)
            .toList());
    return "dashboard";
  }

  @GetMapping("/explore")
  String explore(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Category category,
      @RequestParam(defaultValue = "0") int page,
      Model m) {
    page(m, "explore", "Find your next possibility");
    q = q.strip();
    if (q.length() > 100) q = q.substring(0, 100);
    m.addAttribute("q", q);
    m.addAttribute("category", category);
    m.addAttribute(
        "results",
        skills.search(q, category, PageRequest.of(Math.max(0, Math.min(page, 10000)), 9)));
    return "explore";
  }

  @GetMapping("/skills")
  String skills(Principal p, Model m) {
    page(m, "skills", "Share what you know");
    m.addAttribute("skills", skills.findByOwnerIdOrderByCreatedAtDesc(uid(p)));
    return "skills";
  }

  @GetMapping("/skills/new")
  String newSkill(Model m) {
    page(m, "skills", "Offer a skill");
    m.addAttribute("form", new Forms.SkillForm());
    m.addAttribute("action", "/skills");
    return "skill-form";
  }

  @PostMapping("/skills")
  String newSkill(
      Principal p,
      @Valid @ModelAttribute("form") Forms.SkillForm f,
      BindingResult e,
      Model m,
      RedirectAttributes flash) {
    if (e.hasErrors()) {
      page(m, "skills", "Offer a skill");
      m.addAttribute("action", "/skills");
      return "skill-form";
    }
    skillService.save(null, uid(p), f);
    return saved(flash, "/skills", "Your skill is live in the marketplace.");
  }

  @GetMapping("/skills/{id}/edit")
  String editSkill(@PathVariable Long id, Principal p, Model m) {
    var s = skillService.owned(id, uid(p));
    var f = new Forms.SkillForm();
    f.setTitle(s.getTitle());
    f.setDescription(s.getDescription());
    f.setCategory(s.getCategory());
    page(m, "skills", "Refine your skill listing");
    m.addAttribute("form", f);
    m.addAttribute("action", "/skills/" + id);
    return "skill-form";
  }

  @PostMapping("/skills/{id}")
  String editSkill(
      @PathVariable Long id,
      Principal p,
      @Valid @ModelAttribute("form") Forms.SkillForm f,
      BindingResult e,
      Model m,
      RedirectAttributes flash) {
    skillService.owned(id, uid(p));
    if (e.hasErrors()) {
      page(m, "skills", "Refine your skill listing");
      m.addAttribute("action", "/skills/" + id);
      return "skill-form";
    }
    skillService.save(id, uid(p), f);
    return saved(flash, "/skills", "Skill updated.");
  }

  @PostMapping("/skills/{id}/toggle")
  String toggle(@PathVariable Long id, Principal p, RedirectAttributes f) {
    skillService.toggle(id, uid(p));
    return saved(f, "/skills", "Skill visibility updated. Existing sessions remain available.");
  }

  @GetMapping("/profile")
  String profile(Principal p, Model m) {
    var u = users.current(p.getName());
    var f = new Forms.Profile();
    f.setFullName(u.getFullName());
    f.setBio(u.getBio());
    f.setDepartment(u.getDepartment());
    f.setYearOfStudy(u.getYearOfStudy());
    m.addAttribute("form", f);
    profileData(u.getId(), m);
    return "profile";
  }

  private void profileData(Long id, Model m) {
    page(m, "profile", "A little about you");
    m.addAttribute("interests", interests.findByUserIdOrderBySkillName(id));
    m.addAttribute("skills", skills.findByOwnerIdOrderByCreatedAtDesc(id));
  }

  @PostMapping("/profile")
  String profile(
      Principal p,
      @Valid @ModelAttribute("form") Forms.Profile f,
      BindingResult e,
      Model m,
      RedirectAttributes flash) {
    if (e.hasErrors()) {
      profileData(uid(p), m);
      return "profile";
    }
    users.profile(uid(p), f);
    return saved(flash, "/profile", "Profile updated.");
  }

  @PostMapping("/profile/interests")
  String interest(
      Principal p,
      @Valid @ModelAttribute Forms.InterestForm f,
      BindingResult e,
      RedirectAttributes flash) {
    if (e.hasErrors())
      throw new RuleViolation("Enter an interest of up to 100 characters and choose a category.");
    skillService.addInterest(uid(p), f);
    return saved(flash, "/profile", "Learning interest added.");
  }

  @PostMapping("/profile/interests/{id}/remove")
  String removeInterest(@PathVariable Long id, Principal p, RedirectAttributes f) {
    skillService.removeInterest(id, uid(p));
    return saved(f, "/profile", "Interest removed.");
  }

  @GetMapping("/sessions/request/{skillId}")
  String request(@PathVariable Long skillId, Principal p, Model m) {
    var s = skillService.get(skillId);
    if (!s.isActive() || s.getOwner().getId().equals(uid(p)))
      throw new RuleViolation("Choose an active skill offered by another student.");
    page(m, "sessions", "Make time to learn");
    m.addAttribute("skill", s);
    m.addAttribute("form", new Forms.SessionForm());
    return "request";
  }

  @PostMapping("/sessions/request/{skillId}")
  String request(
      @PathVariable Long skillId,
      Principal p,
      @Valid @ModelAttribute("form") Forms.SessionForm f,
      BindingResult e,
      Model m,
      RedirectAttributes flash) {
    if (!e.hasErrors()) {
      try {
        var s = sessionService.request(skillId, uid(p), f);
        return saved(
            flash,
            "/sessions/" + s.getId(),
            "Request sent. Your teacher can now accept or decline.");
      } catch (RuleViolation ex) {
        e.reject("request", ex.getMessage());
      }
    }
    page(m, "sessions", "Make time to learn");
    m.addAttribute("skill", skillService.get(skillId));
    return "request";
  }

  @GetMapping("/sessions")
  String sessions(Principal p, @RequestParam(defaultValue = "ALL") String status, Model m) {
    page(m, "sessions", "Good things take shared time");
    m.addAttribute("status", status);
    m.addAttribute(
        "sessions",
        sessions.forUser(uid(p)).stream()
            .filter(s -> status.equals("ALL") || s.getStatus().name().equals(status))
            .toList());
    m.addAttribute("statuses", SessionStatus.values());
    return "sessions";
  }

  @GetMapping("/sessions/{id}")
  String session(@PathVariable Long id, Principal p, HttpSession login, Model m) {
    var user = uid(p);
    var s = sessionService.detail(id, user);
    page(m, "sessions", s.getSkill().getTitle());
    m.addAttribute("booking", s);
    m.addAttribute("chatAvailable", conversations.existsBySessionId(id));
    m.addAttribute("meeting", meetings.view(id, user));
    m.addAttribute("googleConfigured", google.configured());
    m.addAttribute("googleConnected", google.connected(login, user));
    var confirmed =
        confirmations.findBySessionId(id).stream()
            .filter(c -> c.getVoidedAt() == null)
            .map(c -> c.getUser().getId())
            .toList();
    m.addAttribute("confirmed", confirmed);
    m.addAttribute("now", clock.instant());
    return "session";
  }

  @PostMapping("/sessions/{id}/{action:accept|decline|cancel|confirm|issue}")
  String action(
      @PathVariable Long id,
      @PathVariable String action,
      Principal p,
      @RequestParam(defaultValue = "") String issue,
      RedirectAttributes flash) {
    var user = uid(p);
    switch (action) {
      case "accept" -> sessionService.accept(id, user);
      case "decline" -> sessionService.decline(id, user);
      case "cancel" -> sessionService.cancel(id, user);
      case "confirm" -> sessionService.confirm(id, user);
      case "issue" -> sessionService.reportIssue(id, user, issue);
    }
    return saved(flash, "/sessions/" + id, "Session updated.");
  }

  @GetMapping("/wallet")
  String wallet(Principal p, Model m) {
    page(m, "wallet", "Your time has value");
    m.addAllAttributes(overview.wallet(uid(p)));
    return "wallet";
  }

  @GetMapping("/ledger")
  String ledger(
      Principal p,
      @RequestParam(defaultValue = "all") String filter,
      @RequestParam(defaultValue = "0") int page,
      Model m) {
    page(m, "wallet", "Every hour, accounted for");
    var all = overview.ledger(uid(p), filter);
    int count = Math.max(1, (all.size() + 19) / 20);
    int index = Math.max(0, Math.min(page, count - 1));
    m.addAttribute("transactions", all.subList(index * 20, Math.min(all.size(), index * 20 + 20)));
    m.addAttribute("filter", filter);
    m.addAttribute("index", index);
    m.addAttribute("pages", count);
    return "ledger";
  }

  @GetMapping("/settings")
  String settings(Model m) {
    page(m, "settings", "Make yourself at home");
    m.addAttribute("form", new Forms.PasswordForm());
    return "settings";
  }

  @PostMapping("/settings/preferences")
  String preferences(
      Principal p, @RequestParam(defaultValue = "false") boolean compact, RedirectAttributes f) {
    users.preferences(uid(p), compact);
    return saved(f, "/settings", "Your preferences have been saved.");
  }

  @PostMapping("/settings/password")
  String password(
      Authentication auth,
      @Valid @ModelAttribute("form") Forms.PasswordForm f,
      BindingResult e,
      Model m,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (!e.hasErrors()) {
      try {
        users.changePassword(uid(auth), f);
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return "redirect:/login?changed";
      } catch (RuleViolation ex) {
        e.reject("password", ex.getMessage());
      }
    }
    page(m, "settings", "Make yourself at home");
    return "settings";
  }
}
