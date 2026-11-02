package com.credlearn.controller;

import com.credlearn.exception.RuleViolation;
import com.credlearn.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sessions/{id}/chat")
@RequiredArgsConstructor
public class ChatApiController {
  private final ChatService chat;
  private final ChatEvents events;
  private final UserService users;

  public record Send(
      @NotBlank @Size(max = 2000) String content,
      @NotBlank @Size(max = 36) String clientMessageId) {}

  public record Read(@Positive long through) {}

  private Long uid(Principal p) {
    return users.current(p.getName()).getId();
  }

  @GetMapping
  public ChatService.History history(
      @PathVariable Long id,
      Principal p,
      @RequestParam(defaultValue = "0") long after,
      @RequestParam(defaultValue = "0") long before,
      HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return chat.history(id, uid(p), after, before);
  }

  @PostMapping
  public ChatService.Message send(
      @PathVariable Long id, Principal p, @Valid @RequestBody Send message) {
    return chat.send(id, uid(p), message.content(), message.clientMessageId());
  }

  @PostMapping("/read")
  public Map<String, Boolean> read(
      @PathVariable Long id, Principal p, @Valid @RequestBody Read message) {
    chat.read(id, uid(p), message.through());
    return Map.of("ok", true);
  }

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @PathVariable Long id, Principal p, HttpSession login, HttpServletResponse response) {
    chat.access(id, uid(p));
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Accel-Buffering", "no");
    return events.subscribe(id, p.getName(), login);
  }

  @ExceptionHandler(RuleViolation.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, String> rule(RuleViolation e) {
    return Map.of("error", e.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  Map<String, String> denied() {
    return Map.of("error", "Only the two session participants can access this chat.");
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, String> invalid() {
    return Map.of("error", "Please check the message and try again.");
  }
}
