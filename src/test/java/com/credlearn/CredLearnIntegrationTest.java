package com.credlearn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.credlearn.dto.Forms;
import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import com.credlearn.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Uses a separate real MySQL schema. Never point this suite at a development/production schema. */
@SpringBootTest
@AutoConfigureMockMvc
class CredLearnIntegrationTest {
  @Autowired UserService users;
  @Autowired SkillService skills;
  @Autowired SessionService sessions;
  @Autowired UserRepository userRepo;
  @Autowired WalletRepository wallets;
  @Autowired SessionRepository sessionRepo;
  @Autowired TransactionRepository transactions;
  @Autowired ConfirmationRepository confirmations;
  @Autowired SkillRepository skillRepo;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired PasswordEncoder passwords;
  @MockitoBean Clock clock;
  @MockitoBean GoogleCalendarGateway googleGateway;
  @Autowired ChatService chats;
  @Autowired ScheduleService schedules;
  @Autowired MeetingService meetings;
  @Autowired MeetingRepository meetingRepo;
  @Autowired ConversationRepository conversationRepo;
  final Instant now = Instant.parse("2026-10-23T10:00:00Z");

  @BeforeEach
  void clean() {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints WHERE"
                + " constraint_schema=DATABASE() AND constraint_name='reject_transfer'",
            Integer.class)
        > 0) jdbc.execute("ALTER TABLE credit_transactions DROP CHECK reject_transfer");
    for (String table :
        List.of(
            "chat_messages",
            "meeting_details",
            "conversations",
            "session_confirmations",
            "credit_transactions",
            "session_requests",
            "credit_wallets",
            "learning_interests",
            "skills",
            "users")) jdbc.execute("DELETE FROM " + table);
    when(clock.instant()).thenReturn(now);
  }

  Forms.Registration registration(String name) {
    var f = new Forms.Registration();
    f.setFullName(name);
    f.setEmail(name.toLowerCase() + "@campus.example");
    f.setPassword("TestPassword!2026");
    f.setConfirmPassword(f.getPassword());
    f.setDepartment("Computer Science");
    f.setYearOfStudy(2);
    return f;
  }

  UserAccount student(String name) {
    return users.register(registration(name));
  }

  Skill skill(UserAccount teacher) {
    var f = new Forms.SkillForm();
    f.setTitle("Java foundations");
    f.setDescription("Learn classes, methods, and your first Java program.");
    f.setCategory(Category.PROGRAMMING);
    return skills.save(null, teacher.getId(), f);
  }

  LearningSession request(Skill skill, UserAccount learner, int minutes, int days) {
    var f = new Forms.SessionForm();
    f.setStartsAt(
        LocalDateTime.ofInstant(now.plusSeconds(days * 86400L), ZoneId.of("Asia/Kolkata")));
    f.setDurationMinutes(minutes);
    return sessions.request(skill.getId(), learner.getId(), f);
  }

  void after(LearningSession s) {
    when(clock.instant()).thenReturn(s.getEndsAt().plusSeconds(1));
  }

  void balance(UserAccount u, String total, String reserved) {
    var w = wallets.findByUserId(u.getId()).orElseThrow();
    assertThat(w.getBalance()).isEqualByComparingTo(total);
    assertThat(w.getReserved()).isEqualByComparingTo(reserved);
  }

  @Test
  void registrationHashesPasswordAndRecordsStarterGrant() {
    var u = student("Alice");
    assertThat(passwords.matches("TestPassword!2026", u.getPasswordHash())).isTrue();
    assertThat(u.getPasswordHash()).doesNotContain("TestPassword");
    balance(u, "3.00", "0");
    var tx = transactions.forUser(u.getId());
    assertThat(tx).hasSize(1);
    assertThat(tx.getFirst().getTransactionType()).isEqualTo("STARTER");
    assertThat(tx.getFirst().getStarterUserId()).isEqualTo(u.getId());
    var duplicate = registration("Alice");
    duplicate.setEmail(" ALICE@CAMPUS.EXAMPLE ");
    assertThatThrownBy(() -> users.register(duplicate)).isInstanceOf(RuleViolation.class);
    assertThat(userRepo.count()).isEqualTo(1);
  }

  @Test
  void registrationFormAndLoginSecurityWork() throws Exception {
    mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    mvc.perform(post("/register").param("email", "alice@campus.example"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("fullName", "Alice")
                .param("email", "alice@campus.example")
                .param("password", "TestPassword!2026")
                .param("confirmPassword", "TestPassword!2026")
                .param("department", "Computer Science")
                .param("yearOfStudy", "2")
                .param("bio", ""))
        .andExpect(redirectedUrl("/login?registered"));
    mvc.perform(
            formLogin("/login").user("email", "ALICE@campus.example").password("TestPassword!2026"))
        .andExpect(authenticated())
        .andExpect(redirectedUrl("/dashboard"));
    mvc.perform(formLogin("/login").user("email", "alice@campus.example").password("wrong"))
        .andExpect(unauthenticated());
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("fullName", "")
                .param("email", "bad")
                .param("password", "short")
                .param("yearOfStudy", "7"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasErrors("form"));
  }

  @Test
  void skillsPersistSearchAndOwnershipIsEnforced() {
    var a = student("Alice");
    var b = student("Bob");
    var s = skill(a);
    assertThat(
            skillRepo
                .search(
                    "JAVA",
                    Category.PROGRAMMING,
                    org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements())
        .isEqualTo(1);
    assertThatThrownBy(() -> skills.toggle(s.getId(), b.getId()))
        .isInstanceOf(AccessDeniedException.class);
    var f = new Forms.SkillForm();
    f.setTitle("Java objects");
    f.setCategory(Category.PROGRAMMING);
    f.setDescription("An introduction to Java objects and their methods.");
    skills.save(s.getId(), a.getId(), f);
    assertThat(skills.get(s.getId()).getTitle()).isEqualTo("Java objects");
    skills.toggle(s.getId(), a.getId());
    assertThat(
            skillRepo
                .search("", null, org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements())
        .isZero();
  }

  @Test
  void selfRequestsPastTimesAndBadDurationsFail() {
    var a = student("Alice");
    var b = student("Bob");
    var sk = skill(a);
    assertThatThrownBy(() -> request(sk, a, 60, 1)).isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> request(sk, b, 60, -1)).isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> request(sk, b, 31, 1)).isInstanceOf(RuleViolation.class);
    assertThat(sessionRepo.count()).isZero();
  }

  @Test
  void creditTransferRequiresTwoValidConfirmationsAndIsExactlyOnce() {
    var a = student("Alice");
    var b = student("Bob");
    var outsider = student("Carol");
    var s = request(skill(a), b, 90, 1);
    assertThatThrownBy(() -> sessions.confirm(s.getId(), b.getId()))
        .isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> sessions.accept(s.getId(), b.getId()))
        .isInstanceOf(AccessDeniedException.class);
    sessions.accept(s.getId(), a.getId());
    balance(b, "3", "1.5");
    assertThatThrownBy(() -> sessions.confirm(s.getId(), b.getId()))
        .isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> sessions.confirm(s.getId(), outsider.getId()))
        .isInstanceOf(AccessDeniedException.class);
    after(s);
    sessions.confirm(s.getId(), a.getId());
    balance(a, "3", "0");
    balance(b, "3", "1.5");
    assertThat(transactions.existsBySessionId(s.getId())).isFalse();
    sessions.confirm(s.getId(), a.getId());
    assertThat(confirmations.countBySessionId(s.getId())).isEqualTo(1);
    sessions.confirm(s.getId(), b.getId());
    balance(a, "4.5", "0");
    balance(b, "1.5", "0");
    sessions.confirm(s.getId(), b.getId());
    assertThat(confirmations.countBySessionId(s.getId())).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM credit_transactions WHERE session_id=?",
                Long.class,
                s.getId()))
        .isEqualTo(1);
    assertThat(sessionRepo.findById(s.getId()).orElseThrow().getStatus())
        .isEqualTo(SessionStatus.COMPLETED);
  }

  @Test
  void declineAndEarlyCancellationReleaseOnlyReservedCredits() {
    var a = student("Alice");
    var b = student("Bob");
    var sk = skill(a);
    var declined = request(sk, b, 60, 1);
    sessions.decline(declined.getId(), a.getId());
    assertThatThrownBy(() -> sessions.accept(declined.getId(), a.getId()))
        .isInstanceOf(RuleViolation.class);
    var s = request(sk, b, 60, 2);
    sessions.accept(s.getId(), a.getId());
    sessions.cancel(s.getId(), b.getId());
    balance(b, "3", "0");
    assertThatThrownBy(() -> sessions.cancel(s.getId(), b.getId()))
        .isInstanceOf(RuleViolation.class);
    balance(b, "3", "0");
    var late = request(sk, b, 60, 3);
    sessions.accept(late.getId(), a.getId());
    after(late);
    assertThatThrownBy(() -> sessions.cancel(late.getId(), a.getId()))
        .isInstanceOf(RuleViolation.class);
    balance(b, "3", "1");
  }

  @Test
  void insufficientFundsAndOverlappingSessionsAreRejected() {
    var a = student("Alice");
    var b = student("Bob");
    var c = student("Carol");
    var sk = skill(a);
    var costly = request(sk, b, 240, 1);
    assertThatThrownBy(() -> sessions.accept(costly.getId(), a.getId()))
        .isInstanceOf(RuleViolation.class);
    balance(b, "3", "0");
    var s = request(sk, b, 60, 1);
    sessions.accept(s.getId(), a.getId());
    var overlap = request(skill(c), b, 60, 1);
    assertThatThrownBy(() -> sessions.accept(overlap.getId(), c.getId()))
        .isInstanceOf(RuleViolation.class);
    balance(b, "3", "1");
  }

  @Test
  void disputeDoesNotTransferUntilBothAgree() {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(a), b, 60, 1);
    sessions.accept(s.getId(), a.getId());
    after(s);
    sessions.reportIssue(
        s.getId(), b.getId(), "We need to agree whether the full session was delivered.");
    balance(b, "3", "1");
    assertThat(transactions.existsBySessionId(s.getId())).isFalse();
    sessions.confirm(s.getId(), a.getId());
    assertThat(transactions.existsBySessionId(s.getId())).isFalse();
    sessions.confirm(s.getId(), b.getId());
    balance(b, "2", "0");
  }

  @Test
  void simultaneousConfirmationsNeverDoubleTransfer() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(a), b, 60, 1);
    sessions.accept(s.getId(), a.getId());
    after(s);
    runTogether(
        () -> sessions.confirm(s.getId(), a.getId()),
        () -> sessions.confirm(s.getId(), b.getId()),
        () -> sessions.confirm(s.getId(), a.getId()),
        () -> sessions.confirm(s.getId(), b.getId()));
    balance(a, "4", "0");
    balance(b, "2", "0");
    assertThat(confirmations.countBySessionId(s.getId())).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM credit_transactions WHERE session_id=?",
                Long.class,
                s.getId()))
        .isEqualTo(1);
  }

  @Test
  void disputeWithdrawsOldConfirmationsAndRequiresFreshAgreement() {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(a), b, 60, 1);
    sessions.accept(s.getId(), a.getId());
    after(s);
    sessions.confirm(s.getId(), a.getId());
    sessions.reportIssue(s.getId(), b.getId(), "The session was interrupted. We need to discuss.");
    assertThat(confirmations.countBySessionIdAndVoidedAtIsNull(s.getId())).isZero();
    sessions.confirm(s.getId(), b.getId());
    assertThat(transactions.existsBySessionId(s.getId())).isFalse();
    sessions.confirm(s.getId(), a.getId());
    balance(a, "4", "0");
    balance(b, "2", "0");
  }

  @Test
  void competingOverlappingSessionsCannotBothBeAccepted() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var c = student("Carol");
    var s1 = request(skill(a), b, 60, 1);
    var s2 = request(skill(c), b, 60, 1);
    var successes = new java.util.concurrent.atomic.AtomicInteger();
    runTogether(
        () -> {
          try {
            sessions.accept(s1.getId(), a.getId());
            successes.incrementAndGet();
          } catch (RuleViolation expected) {
          }
        },
        () -> {
          try {
            sessions.accept(s2.getId(), c.getId());
            successes.incrementAndGet();
          } catch (RuleViolation expected) {
          }
        });
    assertThat(successes.get()).isEqualTo(1);
    balance(b, "3", "1");
  }

  @Test
  void competingReservationsCannotOverspend() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var c = student("Carol");
    var s1 = request(skill(a), b, 120, 1);
    var s2 = request(skill(c), b, 120, 2);
    var successes = new java.util.concurrent.atomic.AtomicInteger();
    runTogether(
        () -> {
          try {
            sessions.accept(s1.getId(), a.getId());
            successes.incrementAndGet();
          } catch (RuleViolation expected) {
          }
        },
        () -> {
          try {
            sessions.accept(s2.getId(), c.getId());
            successes.incrementAndGet();
          } catch (RuleViolation expected) {
          }
        });
    assertThat(successes.get()).isEqualTo(1);
    balance(b, "3", "2");
  }

  @Test
  void failedLedgerInsertRollsBackWalletsAndSecondConfirmation() {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(a), b, 60, 1);
    sessions.accept(s.getId(), a.getId());
    after(s);
    sessions.confirm(s.getId(), a.getId());
    jdbc.execute(
        "ALTER TABLE credit_transactions ADD CONSTRAINT reject_transfer CHECK (transaction_type <>"
            + " 'SESSION')");
    try {
      assertThatThrownBy(() -> sessions.confirm(s.getId(), b.getId()))
          .isInstanceOf(RuntimeException.class);
      balance(a, "3", "0");
      balance(b, "3", "1");
      assertThat(confirmations.countBySessionId(s.getId())).isEqualTo(1);
      assertThat(transactions.existsBySessionId(s.getId())).isFalse();
    } finally {
      jdbc.execute("ALTER TABLE credit_transactions DROP CHECK reject_transfer");
    }
    sessions.confirm(s.getId(), b.getId());
    balance(a, "4", "0");
    balance(b, "2", "0");
  }

  @Test
  void everyPageRendersWithRealDatabaseRecords() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var sk = skill(a);
    var s = request(sk, b, 60, 1);
    for (String path : List.of("/", "/login", "/register"))
      mvc.perform(get(path)).andExpect(status().isOk());
    for (String path :
        List.of(
            "/dashboard",
            "/explore",
            "/explore?q=java&category=PROGRAMMING",
            "/skills",
            "/skills/new",
            "/profile",
            "/sessions",
            "/sessions/" + s.getId(),
            "/sessions/request/" + sk.getId(),
            "/wallet",
            "/ledger",
            "/ledger?filter=starter",
            "/settings"))
      mvc.perform(get(path).with(user(b.getEmail()).roles("STUDENT"))).andExpect(status().isOk());
    mvc.perform(get("/skills/" + sk.getId() + "/edit").with(user(a.getEmail()).roles("STUDENT")))
        .andExpect(status().isOk());
    mvc.perform(get("/skills/" + sk.getId() + "/edit").with(user(b.getEmail()).roles("STUDENT")))
        .andExpect(status().isForbidden());
    sessions.accept(s.getId(), a.getId());
    after(s);
    mvc.perform(get("/sessions/" + s.getId()).with(user(b.getEmail()).roles("STUDENT")))
        .andExpect(status().isOk());
    sessions.confirm(s.getId(), a.getId());
    sessions.confirm(s.getId(), b.getId());
    for (String path :
        List.of(
            "/sessions/" + s.getId(), "/dashboard", "/wallet", "/ledger", "/ledger?filter=earned"))
      mvc.perform(get(path).with(user(a.getEmail()).roles("STUDENT"))).andExpect(status().isOk());
  }

  @Test
  void profileInterestsPreferencesAndPasswordArePersistent() throws Exception {
    var a = student("Alice");
    var f = new Forms.Profile();
    f.setFullName("Alice Updated");
    f.setDepartment("Design");
    f.setYearOfStudy(3);
    f.setBio("Learning every day.");
    users.profile(a.getId(), f);
    users.preferences(a.getId(), true);
    assertThat(users.current(a.getEmail()).getFullName()).isEqualTo("Alice Updated");
    assertThat(users.current(a.getEmail()).isCompactView()).isTrue();
    var interest = new Forms.InterestForm();
    interest.setSkillName("Guitar");
    skills.addInterest(a.getId(), interest);
    assertThatThrownBy(() -> skills.addInterest(a.getId(), interest))
        .isInstanceOf(RuleViolation.class);
    var pass = new Forms.PasswordForm();
    pass.setCurrentPassword("wrong");
    pass.setNewPassword("UpdatedSecret!2026");
    pass.setConfirmPassword(pass.getNewPassword());
    assertThatThrownBy(() -> users.changePassword(a.getId(), pass))
        .isInstanceOf(RuleViolation.class);
    pass.setCurrentPassword("TestPassword!2026");
    users.changePassword(a.getId(), pass);
    mvc.perform(formLogin("/login").user("email", a.getEmail()).password("UpdatedSecret!2026"))
        .andExpect(authenticated());
    mvc.perform(formLogin("/login").user("email", a.getEmail()).password("TestPassword!2026"))
        .andExpect(unauthenticated());
  }

  @Test
  void requestAndSkillFormsWorkEndToEnd() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    mvc.perform(
            post("/skills")
                .with(user(a.getEmail()).roles("STUDENT"))
                .with(csrf())
                .param("title", "Music basics")
                .param("description", "Learn simple chords and strumming for your first song.")
                .param("category", "MUSIC"))
        .andExpect(redirectedUrl("/skills"));
    var sk = skillRepo.findByOwnerIdOrderByCreatedAtDesc(a.getId()).getFirst();
    mvc.perform(
            post("/sessions/request/" + sk.getId())
                .with(user(b.getEmail()).roles("STUDENT"))
                .with(csrf())
                .param("startsAt", "2026-10-24T16:00")
                .param("durationMinutes", "45")
                .param("message", "My first lesson."))
        .andExpect(status().is3xxRedirection());
    assertThat(sessionRepo.forUser(b.getId())).hasSize(1);
    mvc.perform(
            post("/skills/" + sk.getId() + "/toggle")
                .with(user(b.getEmail()).roles("STUDENT"))
                .with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/skills/" + sk.getId() + "/toggle").with(user(a.getEmail()).roles("STUDENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void outsiderCannotViewOrActOnSession() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var c = student("Carol");
    var s = request(skill(a), b, 60, 1);
    mvc.perform(get("/sessions/" + s.getId()).with(user(c.getEmail()).roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/sessions/" + s.getId() + "/accept")
                .with(user(c.getEmail()).roles("STUDENT"))
                .with(csrf()))
        .andExpect(status().isForbidden());
    assertThatThrownBy(() -> sessions.cancel(s.getId(), c.getId()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void adminAccessRequiresPersistedRoleAndAdminLoginUsesRoleRedirect() throws Exception {
    var ordinary = student("Ordinary");
    var admin = student("Administrator");
    admin.setRole(AccountRole.ADMIN);
    userRepo.saveAndFlush(admin);
    mvc.perform(get("/admin/login")).andExpect(status().isOk());
    mvc.perform(get("/admin")).andExpect(redirectedUrl("/admin/login"));
    var studentLogin =
        mvc.perform(
                formLogin("/login")
                    .user("email", ordinary.getEmail())
                    .password("TestPassword!2026"))
            .andExpect(redirectedUrl("/dashboard"))
            .andReturn()
            .getRequest()
            .getSession(false);
    mvc.perform(get("/admin").session((org.springframework.mock.web.MockHttpSession) studentLogin))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/admin/private")
                .session((org.springframework.mock.web.MockHttpSession) studentLogin))
        .andExpect(status().isForbidden());
    var login =
        mvc.perform(
                formLogin("/login").user("email", admin.getEmail()).password("TestPassword!2026"))
            .andExpect(authenticated().withRoles("STUDENT", "ADMIN"))
            .andExpect(redirectedUrl("/admin"))
            .andReturn()
            .getRequest()
            .getSession(false);
    mvc.perform(get("/admin").session((org.springframework.mock.web.MockHttpSession) login))
        .andExpect(status().isOk());
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("email", admin.getEmail())
                .param("password", "wrong")
                .param("loginArea", "admin"))
        .andExpect(unauthenticated())
        .andExpect(redirectedUrl("/admin/login?error"));
    mvc.perform(
            post("/login").param("email", admin.getEmail()).param("password", "TestPassword!2026"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/logout")
                .session((org.springframework.mock.web.MockHttpSession) login)
                .with(csrf()))
        .andExpect(unauthenticated());
    assertThat(((org.springframework.mock.web.MockHttpSession) login).isInvalid()).isTrue();
  }

  @Test
  void publicRegistrationCannotAssignAdminRole() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("fullName", "Mallory")
                .param("email", "mallory@example.com")
                .param("password", "TestPassword!2026")
                .param("confirmPassword", "TestPassword!2026")
                .param("department", "Science")
                .param("yearOfStudy", "2")
                .param("bio", "")
                .param("role", "ADMIN"))
        .andExpect(redirectedUrl("/login?registered"));
    assertThat(users.current("mallory@example.com").getRole()).isEqualTo(AccountRole.STUDENT);
    mvc.perform(
            formLogin("/login").user("email", "mallory@example.com").password("TestPassword!2026"))
        .andExpect(authenticated().withRoles("STUDENT"))
        .andExpect(redirectedUrl("/dashboard"));
  }

  @Test
  void adminTablesRenderActivitySearchSafelyAndNeverExposeHashes() throws Exception {
    var admin = student("Administrator");
    admin.setRole(AccountRole.ADMIN);
    userRepo.saveAndFlush(admin);
    var learner = student("Learner");
    var s = request(skill(admin), learner, 60, 1);
    sessions.accept(s.getId(), admin.getId());
    after(s);
    sessions.confirm(s.getId(), admin.getId());
    sessions.confirm(s.getId(), learner.getId());
    for (String view : List.of("users", "skills", "sessions", "ledger")) {
      var html =
          mvc.perform(
                  get("/admin")
                      .param("view", view)
                      .with(user(admin.getEmail()).roles("STUDENT", "ADMIN")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(html)
          .doesNotContain(admin.getPasswordHash(), learner.getPasswordHash(), "password_hash");
      assertThat(html)
          .contains(
              switch (view) {
                case "skills", "sessions" -> "Java foundations";
                case "ledger" -> "SESSION";
                default -> "learner@campus.example";
              });
    }
    var result =
        mvc.perform(
                get("/admin")
                    .param("q", "learner@campus.example")
                    .param("page", "999")
                    .with(user(admin.getEmail()).roles("STUDENT", "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    var table = (AdminQueryService.Table) result.getModelAndView().getModel().get("table");
    assertThat(table.total()).isEqualTo(1);
    assertThat(table.page()).isZero();
    var injection =
        mvc.perform(
                get("/admin")
                    .param("q", "' OR 1=1 --")
                    .with(user(admin.getEmail()).roles("STUDENT", "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            ((AdminQueryService.Table) injection.getModelAndView().getModel().get("table")).total())
        .isZero();
  }

  private void runTogether(Runnable... actions) throws Exception {
    var ready = new CountDownLatch(actions.length);
    var go = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<?>>();
      for (var action : actions)
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  try {
                    go.await();
                    action.run();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                  }
                }));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      for (var f : futures) f.get(30, TimeUnit.SECONDS);
    }
  }

  @Test
  void chatRequiresAcceptanceAndParticipantMembership() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var outsider = student("Carol");
    var s = request(skill(b), a, 60, 1);
    assertThatThrownBy(() -> chats.history(s.getId(), a.getId(), 0, 0))
        .isInstanceOf(RuleViolation.class);
    sessions.accept(s.getId(), b.getId());
    assertThat(conversationRepo.count()).isEqualTo(1);
    assertThatThrownBy(() -> chats.history(s.getId(), outsider.getId(), 0, 0))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                chats.send(s.getId(), outsider.getId(), "Intrusion", UUID.randomUUID().toString()))
        .isInstanceOf(AccessDeniedException.class);
    mvc.perform(get("/api/sessions/" + s.getId() + "/chat")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/sessions/" + s.getId() + "/chat")
                .with(user(outsider.getEmail()).roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/sessions/" + s.getId() + "/chat/events")
                .with(user(outsider.getEmail()).roles("STUDENT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/sessions/" + s.getId() + "/chat")
                .with(user(a.getEmail()).roles("STUDENT"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/sessions/" + s.getId()).with(user(a.getEmail()).roles("STUDENT")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Open Chat")));
    mvc.perform(get("/sessions/" + s.getId() + "/chat").with(user(a.getEmail()).roles("STUDENT")))
        .andExpect(status().isOk());
    mvc.perform(get("/conversations").with(user(a.getEmail()).roles("STUDENT")))
        .andExpect(status().isOk());
    balance(a, "3", "1");
    assertThat(transactions.count()).isEqualTo(3);
  }

  @Test
  void chatPersistsPlainTextDeduplicatesRetriesAndTracksUnread() {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(b), a, 60, 1);
    sessions.accept(s.getId(), b.getId());
    var key = UUID.randomUUID().toString();
    var payload = "<img src=x onerror=alert(1)> Hello";
    var sent = chats.send(s.getId(), a.getId(), payload, key);
    assertThat(chats.send(s.getId(), a.getId(), payload, key).id()).isEqualTo(sent.id());
    assertThat(chats.history(s.getId(), b.getId(), 0, 0).messages()).hasSize(1);
    assertThat(chats.history(s.getId(), b.getId(), 0, 0).messages().getFirst().content())
        .isEqualTo(payload);
    assertThat(chats.threads(b.getId()).getFirst().unread()).isEqualTo(1);
    chats.read(s.getId(), b.getId(), sent.id());
    assertThat(chats.threads(b.getId()).getFirst().unread()).isZero();
    assertThatThrownBy(
            () -> chats.send(s.getId(), a.getId(), "x".repeat(2001), UUID.randomUUID().toString()))
        .isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> chats.send(s.getId(), a.getId(), " ", UUID.randomUUID().toString()))
        .isInstanceOf(RuleViolation.class);
    assertThatThrownBy(() -> chats.send(s.getId(), a.getId(), "hello", "not-a-uuid"))
        .isInstanceOf(RuleViolation.class);
  }

  @Test
  void cancelledAndCompletedChatsPreserveReadOnlyHistory() {
    var a = student("Alice");
    var b = student("Bob");
    var sk = skill(b);
    var s = request(sk, a, 60, 1);
    sessions.accept(s.getId(), b.getId());
    chats.send(s.getId(), a.getId(), "Saved", UUID.randomUUID().toString());
    sessions.cancel(s.getId(), a.getId());
    assertThat(chats.history(s.getId(), a.getId(), 0, 0).messages()).hasSize(1);
    assertThat(chats.history(s.getId(), a.getId(), 0, 0).writable()).isFalse();
    assertThatThrownBy(() -> chats.send(s.getId(), a.getId(), "Late", UUID.randomUUID().toString()))
        .isInstanceOf(RuleViolation.class);
    var done = request(sk, a, 60, 2);
    sessions.accept(done.getId(), b.getId());
    after(done);
    sessions.confirm(done.getId(), a.getId());
    sessions.confirm(done.getId(), b.getId());
    assertThat(chats.history(done.getId(), a.getId(), 0, 0).writable()).isFalse();
    balance(a, "2", "0");
  }

  @Test
  void declinedAndNeverAcceptedCancelledRequestsHaveNoChat() {
    var a = student("Alice");
    var b = student("Bob");
    var sk = skill(b);
    var s = request(sk, a, 60, 1);
    sessions.decline(s.getId(), b.getId());
    assertThatThrownBy(() -> chats.history(s.getId(), a.getId(), 0, 0))
        .isInstanceOf(RuleViolation.class);
    var cancelled = request(sk, a, 60, 2);
    sessions.cancel(cancelled.getId(), a.getId());
    assertThatThrownBy(() -> chats.history(cancelled.getId(), a.getId(), 0, 0))
        .isInstanceOf(RuleViolation.class);
    assertThat(conversationRepo.count()).isZero();
  }

  @Test
  void scheduleRequiresOtherParticipantAndAdjustsReservationOnly() {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(b), a, 60, 1);
    sessions.accept(s.getId(), b.getId());
    var next = LocalDateTime.ofInstant(now.plusSeconds(2 * 86400), ZoneId.of("Asia/Kolkata"));
    schedules.propose(s.getId(), a.getId(), next, 90);
    var proposed = sessionRepo.findById(s.getId()).orElseThrow();
    balance(a, "3", "1");
    assertThat(proposed.getStartsAt()).isEqualTo(s.getStartsAt());
    assertThatThrownBy(
            () -> schedules.approve(s.getId(), a.getId(), proposed.getScheduleRevision()))
        .isInstanceOf(RuleViolation.class);
    schedules.approve(s.getId(), b.getId(), proposed.getScheduleRevision());
    balance(a, "3", "1.5");
    assertThat(sessionRepo.findById(s.getId()).orElseThrow().getDurationMinutes()).isEqualTo(90);
    assertThatThrownBy(
            () -> schedules.approve(s.getId(), b.getId(), proposed.getScheduleRevision()))
        .isInstanceOf(RuleViolation.class);
    assertThat(transactions.count()).isEqualTo(2);
  }

  @Test
  void scheduleRejectsInsufficientCreditsAndOverlapsWithoutPartialChanges() {
    var a = student("Alice");
    var b = student("Bob");
    var sk = skill(b);
    var s = request(sk, a, 60, 1);
    sessions.accept(s.getId(), b.getId());
    var next = LocalDateTime.ofInstant(now.plusSeconds(3 * 86400), ZoneId.of("Asia/Kolkata"));
    schedules.propose(s.getId(), a.getId(), next, 240);
    var proposed = sessionRepo.findById(s.getId()).orElseThrow();
    assertThatThrownBy(
            () -> schedules.approve(s.getId(), b.getId(), proposed.getScheduleRevision()))
        .isInstanceOf(RuleViolation.class);
    balance(a, "3", "1");
    schedules.dismiss(s.getId(), a.getId(), proposed.getScheduleRevision());
    var other = request(sk, a, 60, 2);
    sessions.accept(other.getId(), b.getId());
    schedules.propose(
        s.getId(),
        a.getId(),
        LocalDateTime.ofInstant(other.getStartsAt(), ZoneId.of("Asia/Kolkata")),
        60);
    var rev = sessionRepo.findById(s.getId()).orElseThrow().getScheduleRevision();
    assertThatThrownBy(() -> schedules.approve(s.getId(), b.getId(), rev))
        .isInstanceOf(RuleViolation.class);
    balance(a, "3", "2");
  }

  GoogleConnection.Tokens googleToken(UserAccount who) {
    return new GoogleConnection.Tokens(
        who.getId(), "google-test-subject", "test-token", "test-refresh", now.plusSeconds(3600));
  }

  @Test
  void meetingRetriesReuseDurableEventAndDoNotTransferCredits() throws Exception {
    var a = student("Alice");
    var b = student("Bob");
    var s = request(skill(b), a, 60, 1);
    sessions.accept(s.getId(), b.getId());
    when(googleGateway.ensure(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("simulated timeout"))
        .thenReturn(new GoogleCalendarGateway.Result("PENDING", null, "Preparing"))
        .thenReturn(
            new GoogleCalendarGateway.Result("READY", "https://meet.google.com/abc-defg-hij", ""));
    var first = meetings.create(s.getId(), a.getId(), googleToken(a));
    assertThat(first.getStatus()).isEqualTo("ERROR");
    assertThat(meetings.create(s.getId(), a.getId(), googleToken(a)).getStatus())
        .isEqualTo("PENDING");
    var ready = meetings.create(s.getId(), a.getId(), googleToken(a));
    assertThat(ready.getExternalEventId()).isEqualTo(first.getExternalEventId());
    assertThat(ready.getStatus()).isEqualTo("READY");
    assertThat(meetings.view(s.getId(), b.getId()).getMeetingUrl())
        .isEqualTo(ready.getMeetingUrl());
    for (var path :
        List.of("/sessions/" + s.getId(), "/sessions/" + s.getId() + "/chat", "/dashboard"))
      mvc.perform(get(path).with(user(b.getEmail()).roles("STUDENT"))).andExpect(status().isOk());
    meetings.create(s.getId(), b.getId(), googleToken(b));
    assertThat(meetingRepo.count()).isEqualTo(1);
    org.mockito.Mockito.verify(googleGateway, org.mockito.Mockito.times(3))
        .ensure(
            org.mockito.ArgumentMatchers.eq("test-token"),
            org.mockito.ArgumentMatchers.eq(first.getExternalEventId()),
            org.mockito.ArgumentMatchers.eq(first.getConferenceRequestId()),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    balance(a, "3", "1");
    assertThat(transactions.count()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                schedules.propose(
                    s.getId(),
                    a.getId(),
                    LocalDateTime.ofInstant(now.plusSeconds(86400 * 2), ZoneId.of("Asia/Kolkata")),
                    60))
        .isInstanceOf(RuleViolation.class);
  }

  @Test
  void meetingsRejectPendingOutsidersUnagreedSchedulesAndAccountSwitches() {
    var a = student("Alice");
    var b = student("Bob");
    var c = student("Carol");
    var s = request(skill(b), a, 60, 1);
    assertThatThrownBy(() -> meetings.create(s.getId(), a.getId(), googleToken(a)))
        .isInstanceOf(RuleViolation.class);
    sessions.accept(s.getId(), b.getId());
    assertThatThrownBy(() -> meetings.view(s.getId(), c.getId()))
        .isInstanceOf(AccessDeniedException.class);
    schedules.propose(
        s.getId(),
        a.getId(),
        LocalDateTime.ofInstant(now.plusSeconds(86400 * 2), ZoneId.of("Asia/Kolkata")),
        60);
    assertThatThrownBy(() -> meetings.create(s.getId(), a.getId(), googleToken(a)))
        .isInstanceOf(RuleViolation.class);
    schedules.dismiss(
        s.getId(), a.getId(), sessionRepo.findById(s.getId()).orElseThrow().getScheduleRevision());
    when(googleGateway.ensure(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(new GoogleCalendarGateway.Result("PENDING", null, "Preparing"));
    meetings.create(s.getId(), a.getId(), googleToken(a));
    assertThatThrownBy(() -> meetings.create(s.getId(), b.getId(), googleToken(b)))
        .isInstanceOf(RuleViolation.class);
    var switched =
        new GoogleConnection.Tokens(
            a.getId(), "different-account", "token", "refresh", now.plusSeconds(3600));
    assertThatThrownBy(() -> meetings.create(s.getId(), a.getId(), switched))
        .isInstanceOf(RuleViolation.class);
    assertThat(meetingRepo.count()).isEqualTo(1);
  }
}
