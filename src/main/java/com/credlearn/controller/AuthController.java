package com.credlearn.controller;

import com.credlearn.dto.Forms;
import com.credlearn.exception.RuleViolation;
import com.credlearn.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AuthController {
  private final UserService users;

  @GetMapping("/")
  String landing() {
    return "landing";
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }

  @GetMapping("/register")
  String register(Model m) {
    m.addAttribute("form", new Forms.Registration());
    return "register";
  }

  @PostMapping("/register")
  String register(@Valid @ModelAttribute("form") Forms.Registration f, BindingResult errors) {
    if (errors.hasErrors()) return "register";
    try {
      users.register(f);
    } catch (RuleViolation e) {
      errors.reject("registration", e.getMessage());
      return "register";
    } catch (DataIntegrityViolationException e) {
      errors.reject("registration", "This email is already registered.");
      return "register";
    }
    return "redirect:/login?registered";
  }
}
