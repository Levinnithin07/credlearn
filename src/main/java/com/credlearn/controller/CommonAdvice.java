package com.credlearn.controller;

import com.credlearn.entity.Category;
import com.credlearn.exception.RuleViolation;
import com.credlearn.service.UserService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
@RequiredArgsConstructor
public class CommonAdvice {
  private final UserService users;

  @Value("${credlearn.chat.transport:sse}")
  private String chatTransport;

  @ModelAttribute
  void common(Principal p, Model model) {
    model.addAttribute("categories", Category.values());
    model.addAttribute("chatTransport", chatTransport);
    if (p != null) model.addAttribute("me", users.current(p.getName()));
  }

  @ExceptionHandler(RuleViolation.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  String rule(RuleViolation e, Model m) {
    m.addAttribute("errorTitle", "Let's fix that");
    m.addAttribute("errorMessage", e.getMessage());
    return "error";
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  String forbidden(Model m) {
    m.addAttribute("errorTitle", "This page is private");
    m.addAttribute("errorMessage", "You do not have permission to access or change this item.");
    return "error";
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  String conflict(Model m) {
    m.addAttribute("errorTitle", "That change could not be saved");
    m.addAttribute(
        "errorMessage",
        "A record already exists or another change happened at the same time. Refresh and try"
            + " again.");
    return "error";
  }
}
