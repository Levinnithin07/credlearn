package com.credlearn.service;

import com.credlearn.dto.Forms;
import com.credlearn.entity.*;
import com.credlearn.exception.RuleViolation;
import com.credlearn.repository.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
  private final UserRepository users;
  private final WalletRepository wallets;
  private final TransactionRepository transactions;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public UserAccount current(String email) {
    return users.findByEmail(email).orElseThrow(() -> new RuleViolation("Account not found."));
  }

  public UserAccount register(Forms.Registration f) {
    String email = f.getEmail().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email))
      throw new RuleViolation("An account already uses this email. Please sign in.");
    validatePassword(f.getPassword(), f.getConfirmPassword());
    var u = new UserAccount();
    u.setRole(AccountRole.STUDENT);
    u.setFullName(f.getFullName().trim());
    u.setEmail(email);
    u.setPasswordHash(encoder.encode(f.getPassword()));
    u.setDepartment(f.getDepartment().trim());
    u.setYearOfStudy(f.getYearOfStudy());
    u.setBio(f.getBio().trim());
    users.saveAndFlush(u);
    var wallet = new Wallet();
    wallet.setUser(u);
    wallet.setBalance(new BigDecimal("3.00"));
    wallets.save(wallet);
    var tx = new CreditTransaction();
    tx.setReceiver(u);
    tx.setAmount(new BigDecimal("3.00"));
    tx.setTransactionType("STARTER");
    tx.setStarterUserId(u.getId());
    tx.setCreatedAt(clock.instant());
    transactions.save(tx);
    return u;
  }

  public void profile(Long id, Forms.Profile f) {
    var u = users.findById(id).orElseThrow();
    u.setFullName(f.getFullName().trim());
    u.setDepartment(f.getDepartment().trim());
    u.setYearOfStudy(f.getYearOfStudy());
    u.setBio(f.getBio().trim());
  }

  public void preferences(Long id, boolean compact) {
    users.findById(id).orElseThrow().setCompactView(compact);
  }

  public void changePassword(Long id, Forms.PasswordForm f) {
    var u = users.findById(id).orElseThrow();
    if (!encoder.matches(f.getCurrentPassword(), u.getPasswordHash()))
      throw new RuleViolation("Your current password is incorrect.");
    validatePassword(f.getNewPassword(), f.getConfirmPassword());
    u.setPasswordHash(encoder.encode(f.getNewPassword()));
  }

  private void validatePassword(String password, String confirmation) {
    if (!password.equals(confirmation)) throw new RuleViolation("Passwords do not match.");
    if (password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new RuleViolation(
          "Use at least 10 characters and at most 72 UTF-8 bytes for your password.");
  }
}
