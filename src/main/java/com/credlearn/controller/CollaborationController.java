package com.credlearn.controller;

import com.credlearn.service.*;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CollaborationController {
  private final UserService users;
  private final ChatService chats;
  private final SessionService sessions;
  private final ScheduleService schedules;
  private final MeetingService meetings;
  private final GoogleConnection google;
  private final Clock clock;

  private Long uid(Principal p) {
    return users.current(p.getName()).getId();
  }

  @GetMapping("/conversations")
  String conversations(Principal p, Model m) {
    m.addAttribute("section", "chat");
    m.addAttribute("pageTitle", "Your conversations");
    m.addAttribute("threads", chats.threads(uid(p)));
    return "conversations";
  }

  @GetMapping("/sessions/{id}/chat")
  String chat(@PathVariable Long id, Principal p, HttpSession login, Model m) {
    var user = uid(p);
    chats.access(id, user);
    var s = sessions.detail(id, user);
    m.addAttribute("section", "chat");
    m.addAttribute("pageTitle", s.getSkill().getTitle());
    m.addAttribute("booking", s);
    m.addAttribute(
        "peer", s.getRequester().getId().equals(user) ? s.getProvider() : s.getRequester());
    m.addAttribute("writable", chats.writable(s));
    m.addAttribute("now", clock.instant());
    m.addAttribute("meeting", meetings.view(id, user));
    m.addAttribute("googleConfigured", google.configured());
    m.addAttribute("googleConnected", google.connected(login, user));
    return "chat";
  }

  @PostMapping("/sessions/{id}/schedule/propose")
  String propose(
      @PathVariable Long id,
      Principal p,
      @RequestParam LocalDateTime startsAt,
      @RequestParam int durationMinutes,
      RedirectAttributes flash) {
    schedules.propose(id, uid(p), startsAt, durationMinutes);
    flash.addFlashAttribute(
        "success",
        "Schedule proposal sent. The original time remains agreed until the other participant"
            + " approves.");
    return "redirect:/sessions/" + id;
  }

  @PostMapping("/sessions/{id}/schedule/approve")
  String approve(
      @PathVariable Long id, Principal p, @RequestParam long revision, RedirectAttributes flash) {
    schedules.approve(id, uid(p), revision);
    flash.addFlashAttribute(
        "success", "Both participants agreed. Schedule and reserved credits updated.");
    return "redirect:/sessions/" + id;
  }

  @PostMapping("/sessions/{id}/schedule/dismiss")
  String dismiss(
      @PathVariable Long id, Principal p, @RequestParam long revision, RedirectAttributes flash) {
    schedules.dismiss(id, uid(p), revision);
    flash.addFlashAttribute("success", "Proposal dismissed. The existing schedule remains agreed.");
    return "redirect:/sessions/" + id;
  }

  @PostMapping("/sessions/{id}/google/connect")
  String connect(@PathVariable Long id, Principal p, HttpSession login) {
    var user = uid(p);
    meetings.eligible(id, user);
    return "redirect:" + google.begin(login, user, id);
  }

  @GetMapping("/google/callback")
  String callback(
      Principal p,
      HttpSession login,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String error,
      RedirectAttributes flash) {
    var id = google.finish(login, uid(p), state, code, error);
    flash.addFlashAttribute(
        "success", "Google account connected. You can now generate the meeting link.");
    return "redirect:/sessions/" + id;
  }

  @PostMapping("/sessions/{id}/google/disconnect")
  String disconnect(@PathVariable Long id, Principal p, HttpSession login) {
    sessions.detail(id, uid(p));
    google.disconnect(login);
    return "redirect:/sessions/" + id;
  }

  @PostMapping("/sessions/{id}/meeting")
  String meeting(@PathVariable Long id, Principal p, HttpSession login, RedirectAttributes flash) {
    var user = uid(p);
    meetings.eligible(id, user);
    var m = meetings.create(id, user, google.credentials(login, user));
    flash.addFlashAttribute(
        "success",
        "READY".equals(m.getStatus())
            ? "Google Meet link created and shared with both participants."
            : m.getLastError());
    return "redirect:/sessions/" + id;
  }
}
