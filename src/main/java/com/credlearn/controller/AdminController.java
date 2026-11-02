package com.credlearn.controller;

import com.credlearn.service.AdminQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AdminController {
  private final AdminQueryService admin;

  @GetMapping("/admin/login")
  String login() {
    return "admin-login";
  }

  @GetMapping({"/admin", "/admin/"})
  String dashboard(
      @RequestParam(defaultValue = "users") String view,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    var table = admin.table(view, q, page);
    model.addAttribute("table", table);
    model.addAttribute("counts", admin.counts());
    model.addAttribute("pageTitle", "Your community, at a glance");
    model.addAttribute("section", "admin");
    return "admin";
  }
}
