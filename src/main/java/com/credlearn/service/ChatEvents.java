package com.credlearn.service;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Streams invalidation signals only. Message contents always pass authorized HTTP retrieval. */
@Component
@EnableScheduling
public class ChatEvents {
  private record Client(Long sessionId, String email, HttpSession login, SseEmitter emitter) {}

  private final Map<UUID, Client> clients = new ConcurrentHashMap<>();

  public SseEmitter subscribe(Long sessionId, String email, HttpSession login) {
    if (clients.values().stream().filter(c -> c.email.equals(email)).count() >= 8)
      throw new com.credlearn.exception.RuleViolation(
          "Too many open chat tabs. Close another chat and retry.");
    var emitter = new SseEmitter(300_000L);
    var id = UUID.randomUUID();
    var client = new Client(sessionId, email, login, emitter);
    clients.put(id, client);
    emitter.onCompletion(() -> clients.remove(id));
    emitter.onTimeout(
        () -> {
          clients.remove(id);
          emitter.complete();
        });
    emitter.onError(error -> clients.remove(id));
    send(id, client, "connected");
    return emitter;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void changed(SessionChanged event) {
    clients.forEach(
        (id, c) -> {
          if (c.sessionId.equals(event.sessionId())) send(id, c, "changed");
        });
  }

  @Scheduled(fixedDelay = 15000)
  public void heartbeat() {
    clients.forEach((id, c) -> send(id, c, "heartbeat"));
  }

  private void send(UUID id, Client c, String event) {
    try {
      var context = c.login.getAttribute("SPRING_SECURITY_CONTEXT");
      if (!(context instanceof SecurityContext security)
          || security.getAuthentication() == null
          || !security.getAuthentication().isAuthenticated()
          || !security.getAuthentication().getName().equals(c.email)
          || (c.login.getMaxInactiveInterval() > 0
              && System.currentTimeMillis() - c.login.getLastAccessedTime()
                  > c.login.getMaxInactiveInterval() * 1000L)) {
        clients.remove(id);
        c.emitter.complete();
        return;
      }
      c.emitter.send(SseEmitter.event().name(event).data("refresh"));
    } catch (Exception e) {
      clients.remove(id);
      c.emitter.complete();
    }
  }
}
