package com.credlearn;

import static org.assertj.core.api.Assertions.*;

import com.credlearn.dto.Forms;
import com.credlearn.entity.*;
import com.credlearn.service.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Two independent HTTP servers share only MySQL and an encryption key, never process sessions. */
class SharedSessionIntegrationTest {
  private static final String PASSWORD = "SharedSession-Test-2026!";

  @Test
  void loginCsrfChatAndLogoutWorkAcrossInstancesAndRestart() throws Exception {
    assertThat(System.getenv("DB_URL"))
        .as("Only the disposable test schema is allowed")
        .contains("/skillswap_test?");
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String key = Base64.getEncoder().encodeToString(bytes);
    try (var a = start(key);
        var b = start(key)) {
      var jdbc = a.getBean(JdbcTemplate.class);
      assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
          .isEqualTo("skillswap_test");
      jdbc.update("DELETE FROM credlearn_http_sessions");
      var users = a.getBean(UserService.class);
      String suffix = UUID.randomUUID().toString().substring(0, 8);
      var learner = users.register(registration("Learner", suffix));
      var teacher = users.register(registration("Teacher", suffix));
      var outsider = users.register(registration("Outsider", suffix));
      var f = new Forms.SkillForm();
      f.setTitle("Multi instance Java");
      f.setDescription("An integration test of private session communication across containers.");
      f.setCategory(Category.PROGRAMMING);
      var skill = a.getBean(SkillService.class).save(null, teacher.getId(), f);
      var schedule = new Forms.SessionForm();
      schedule.setStartsAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(10));
      schedule.setDurationMinutes(60);
      var sessions = a.getBean(SessionService.class);
      var booking = sessions.request(skill.getId(), learner.getId(), schedule);
      sessions.accept(booking.getId(), teacher.getId());
      var client = client();
      login(client, a, learner.getEmail());
      var page = get(client, b, "/sessions/" + booking.getId() + "/chat");
      assertThat(page.statusCode()).isEqualTo(200);
      assertThat(page.body()).contains("data-transport=\"poll\"");
      String csrf = csrf(page.body());
      String path = "/api/sessions/" + booking.getId() + "/chat";
      var sent =
          post(
              client,
              b,
              path,
              "{\"content\":\"Shared private message\",\"clientMessageId\":\""
                  + UUID.randomUUID()
                  + "\"}",
              csrf,
              true);
      assertThat(sent.statusCode()).isEqualTo(200);
      var peer = client();
      login(peer, b, teacher.getEmail());
      assertThat(get(peer, a, path).body()).contains("Shared private message");
      var stranger = client();
      login(stranger, a, outsider.getEmail());
      assertThat(get(stranger, b, path).statusCode()).isEqualTo(403);
      assertThat(post(client, a, path, "{}", null, true).statusCode()).isEqualTo(403);
      // Each attribute must be encrypted, including the stored security context.
      var attributes =
          jdbc.queryForList(
              "SELECT ATTRIBUTE_BYTES FROM credlearn_http_sessions_ATTRIBUTES", byte[].class);
      assertThat(attributes).isNotEmpty();
      for (byte[] value : attributes)
        assertThat(value[0] == (byte) 0xAC && value[1] == (byte) 0xED).isFalse();
      // Simulate a newly started container with the same key and database.
      try (var restarted = start(key)) {
        assertThat(get(client, restarted, path).statusCode()).isEqualTo(200);
        var logoutPage = get(client, restarted, "/dashboard");
        assertThat(
                post(
                        client,
                        restarted,
                        "/logout",
                        "_csrf=" + encode(csrf(logoutPage.body())),
                        null,
                        false)
                    .statusCode())
            .isEqualTo(302);
        assertThat(get(client, b, path).statusCode()).isEqualTo(401);
      }
      assertThat(
              a.getBean(com.credlearn.repository.WalletRepository.class)
                  .findByUserId(learner.getId())
                  .orElseThrow()
                  .getBalance())
          .isEqualByComparingTo("3.00");
    }
  }

  private ServletWebServerApplicationContext start(String key) {
    return (ServletWebServerApplicationContext)
        new SpringApplicationBuilder(CredLearnApplication.class)
            .run(
                "--spring.profiles.active=vercel",
                "--server.port=0",
                "--server.address=127.0.0.1",
                "--server.servlet.session.cookie.secure=false",
                "--credlearn.session-key=" + key,
                "--spring.datasource.hikari.minimum-idle=0",
                "--spring.datasource.hikari.maximum-pool-size=3");
  }

  private Forms.Registration registration(String name, String suffix) {
    var f = new Forms.Registration();
    f.setFullName(name + " Test");
    f.setEmail(name.toLowerCase() + "." + suffix + "@campus.example");
    f.setPassword(PASSWORD);
    f.setConfirmPassword(PASSWORD);
    f.setDepartment("Computer Science");
    f.setYearOfStudy(2);
    return f;
  }

  private HttpClient client() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .build();
  }

  private URI uri(ServletWebServerApplicationContext app, String path) {
    return URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path);
  }

  private HttpResponse<String> get(
      HttpClient client, ServletWebServerApplicationContext app, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri(app, path)).timeout(Duration.ofSeconds(15)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(
      HttpClient client,
      ServletWebServerApplicationContext app,
      String path,
      String body,
      String csrf,
      boolean json)
      throws Exception {
    var req =
        HttpRequest.newBuilder(uri(app, path))
            .timeout(Duration.ofSeconds(15))
            .header(
                "Content-Type", json ? "application/json" : "application/x-www-form-urlencoded");
    if (csrf != null) req.header("X-CSRF-TOKEN", csrf);
    return client.send(
        req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private void login(HttpClient client, ServletWebServerApplicationContext app, String email)
      throws Exception {
    String token = csrf(get(client, app, "/login").body());
    var result =
        post(
            client,
            app,
            "/login",
            "email=" + encode(email) + "&password=" + encode(PASSWORD) + "&_csrf=" + encode(token),
            null,
            false);
    assertThat(result.headers().firstValue("Location").orElseThrow()).endsWith("/dashboard");
  }

  private static String csrf(String html) {
    var match = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\"").matcher(html);
    assertThat(match.find()).isTrue();
    return match.group(1);
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
