package com.credlearn.service;

import com.credlearn.entity.*;
import com.credlearn.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewService {
  private final WalletRepository wallets;
  private final SessionRepository sessions;
  private final SkillRepository skills;
  private final TransactionRepository transactions;
  private final ConfirmationRepository confirmations;
  private final Clock clock;

  public Map<String, Object> dashboard(Long id) {
    var result = new HashMap<String, Object>();
    var all = sessions.forUser(id);
    var tx = transactions.forUser(id);
    result.put("wallet", wallets.findByUserId(id).orElseThrow());
    result.put(
        "offeredCount",
        skills.findByOwnerIdOrderByCreatedAtDesc(id).stream().filter(Skill::isActive).count());
    result.put(
        "pendingCount", all.stream().filter(s -> s.getStatus() == SessionStatus.PENDING).count());
    result.put(
        "confirmCount",
        all.stream()
            .filter(
                s ->
                    (s.getStatus() == SessionStatus.ACCEPTED
                            || s.getStatus() == SessionStatus.DISPUTED)
                        && !s.getEndsAt().isAfter(clock.instant())
                        && !confirmations.existsBySessionIdAndUserIdAndVoidedAtIsNull(
                            s.getId(), id))
            .count());
    result.put(
        "upcoming",
        all.stream()
            .filter(
                s ->
                    s.getStatus() == SessionStatus.ACCEPTED
                        && s.getEndsAt().isAfter(clock.instant()))
            .sorted(Comparator.comparing(LearningSession::getStartsAt))
            .limit(3)
            .toList());
    result.put("recent", tx.stream().limit(4).toList());
    return result;
  }

  public Map<String, Object> wallet(Long id) {
    var result = new HashMap<String, Object>();
    var tx = transactions.forUser(id);
    result.put("wallet", wallets.findByUserId(id).orElseThrow());
    result.put(
        "earned",
        tx.stream()
            .filter(
                t -> t.getTransactionType().equals("SESSION") && t.getReceiver().getId().equals(id))
            .map(CreditTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    result.put(
        "spent",
        tx.stream()
            .filter(t -> t.getSender() != null && t.getSender().getId().equals(id))
            .map(CreditTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    result.put("recent", tx.stream().limit(6).toList());
    return result;
  }

  public List<CreditTransaction> ledger(Long id, String filter) {
    return transactions.forUser(id).stream()
        .filter(
            t ->
                switch (filter) {
                  case "earned" ->
                      t.getTransactionType().equals("SESSION")
                          && t.getReceiver().getId().equals(id);
                  case "spent" -> t.getSender() != null && t.getSender().getId().equals(id);
                  case "starter" -> t.getTransactionType().equals("STARTER");
                  default -> true;
                })
        .toList();
  }
}
