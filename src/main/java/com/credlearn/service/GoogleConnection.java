package com.credlearn.service;

import com.credlearn.exception.RuleViolation;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Google authorization supplements, and never replaces, the local student login. */
@Service
public class GoogleConnection {
  private static final String ATTEMPT = "CREDLEARN_GOOGLE_ATTEMPT",
      TOKENS = "CREDLEARN_GOOGLE_TOKENS";
  private static final String SCOPE =
      "openid https://www.googleapis.com/auth/calendar.events.owned";

  @Value("${credlearn.google.client-id:}")
  private String clientId;

  @Value("${credlearn.google.client-secret:}")
  private String clientSecret;

  @Value("${credlearn.google.redirect-uri:http://127.0.0.1:8080/google/callback}")
  private String callback;

  private final RestClient http = httpClient();

  record Attempt(Long user, Long session, String state, String verifier, Instant created)
      implements java.io.Serializable {}

  public record Tokens(Long user, String subject, String access, String refresh, Instant expires)
      implements java.io.Serializable {}

  static RestClient httpClient() {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(Duration.ofSeconds(15));
    return RestClient.builder().requestFactory(factory).build();
  }

  public boolean configured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  public boolean connected(HttpSession session, Long user) {
    return session != null
        && session.getAttribute(TOKENS) instanceof Tokens t
        && t.user.equals(user);
  }

  public String begin(HttpSession session, Long user, Long booking) {
    if (!configured())
      throw new RuleViolation(
          "Google Meet is awaiting local Google Cloud configuration. See docs/GOOGLE-SETUP.md.");
    var state = random();
    var verifier = random();
    session.setAttribute(ATTEMPT, new Attempt(user, booking, state, verifier, Instant.now()));
    String challenge;
    try {
      challenge =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  MessageDigest.getInstance("SHA-256")
                      .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", callback)
        .queryParam("response_type", "code")
        .queryParam("scope", SCOPE)
        .queryParam("state", state)
        .queryParam("code_challenge", challenge)
        .queryParam("code_challenge_method", "S256")
        .queryParam("access_type", "offline")
        .queryParam("prompt", "consent")
        .build()
        .encode()
        .toUriString();
  }

  public Long finish(HttpSession session, Long user, String state, String code, String error) {
    var value = session.getAttribute(ATTEMPT);
    session.removeAttribute(ATTEMPT);
    if (!(value instanceof Attempt a)
        || !a.user.equals(user)
        || state == null
        || !MessageDigest.isEqual(
            a.state.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))
        || a.created.plusSeconds(600).isBefore(Instant.now()))
      throw new RuleViolation(
          "Google authorization expired or could not be verified. Start again from your session.");
    if (error != null || code == null)
      throw new RuleViolation(
          "Google access was not granted. Your CredLearn session is unchanged.");
    try {
      var form = form();
      form.add("code", code);
      form.add("redirect_uri", callback);
      form.add("grant_type", "authorization_code");
      form.add("code_verifier", a.verifier);
      var token = token(form);
      var identity =
          http.get()
              .uri("https://openidconnect.googleapis.com/v1/userinfo")
              .headers(h -> h.setBearerAuth(token.path("access_token").asText()))
              .retrieve()
              .body(JsonNode.class);
      if (identity == null || identity.path("sub").asText().isBlank())
        throw new IllegalStateException();
      session.setAttribute(
          TOKENS,
          new Tokens(
              user,
              identity.path("sub").asText(),
              token.path("access_token").asText(),
              token.path("refresh_token").asText(),
              Instant.now().plusSeconds(token.path("expires_in").asLong(3600))));
      return a.session;
    } catch (Exception ex) {
      throw new RuleViolation(
          "Google authorization could not be completed. Check the local OAuth settings and try"
              + " connecting again.");
    }
  }

  public Tokens credentials(HttpSession session, Long user) {
    synchronized (session) {
      if (!connected(session, user))
        throw new RuleViolation(
            "Connect your Google account first. Only the meeting organizer needs to connect.");
      var t = (Tokens) session.getAttribute(TOKENS);
      if (t.expires.isAfter(Instant.now().plusSeconds(60))) return t;
      try {
        if (t.refresh.isBlank()) throw new IllegalStateException();
        var form = form();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", t.refresh);
        var response = token(form);
        var fresh =
            new Tokens(
                user,
                t.subject,
                response.path("access_token").asText(),
                response.path("refresh_token").asText(t.refresh),
                Instant.now().plusSeconds(response.path("expires_in").asLong(3600)));
        session.setAttribute(TOKENS, fresh);
        return fresh;
      } catch (Exception e) {
        session.removeAttribute(TOKENS);
        throw new RuleViolation(
            "Google permission expired or was revoked. Connect your Google account again.");
      }
    }
  }

  public void disconnect(HttpSession session) {
    session.removeAttribute(TOKENS);
    session.removeAttribute(ATTEMPT);
  }

  private LinkedMultiValueMap<String, String> form() {
    var f = new LinkedMultiValueMap<String, String>();
    f.add("client_id", clientId);
    f.add("client_secret", clientSecret);
    return f;
  }

  private JsonNode token(LinkedMultiValueMap<String, String> form) {
    var result =
        http.post()
            .uri("https://oauth2.googleapis.com/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
    if (result == null || result.path("access_token").asText().isBlank())
      throw new IllegalStateException();
    return result;
  }

  private String random() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
