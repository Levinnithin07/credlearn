package com.credlearn.service;

import com.credlearn.util.Display;
import java.sql.Timestamp;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberately selects display fields only; password hashes never enter admin view models. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("@ownerAdminPolicy.allowed(authentication)")
public class AdminQueryService {
  private final JdbcTemplate jdbc;
  private final Display display;

  public record Column(String key, String label) {}

  public record Table(
      String view,
      String query,
      List<Column> columns,
      List<Map<String, Object>> rows,
      long total,
      int page,
      int pages) {}

  public Map<String, Long> counts() {
    return Map.of(
        "students", count("SELECT COUNT(*) FROM users WHERE role='STUDENT'"),
        "skills", count("SELECT COUNT(*) FROM skills WHERE active=TRUE"),
        "sessions", count("SELECT COUNT(*) FROM session_requests"),
        "disputes", count("SELECT COUNT(*) FROM session_requests WHERE status='DISPUTED'"));
  }

  private long count(String sql) {
    return jdbc.queryForObject(sql, Long.class);
  }

  public Table table(String requestedView, String query, int requestedPage) {
    String view =
        Set.of("users", "skills", "sessions", "ledger").contains(requestedView)
            ? requestedView
            : "users";
    String q = view.equals("users") ? query.strip() : "";
    if (q.length() > 100) q = q.substring(0, 100);
    String from;
    String fields;
    List<Column> columns;
    List<Object> parameters = new ArrayList<>();
    switch (view) {
      case "skills" -> {
        from = " FROM skills s JOIN users u ON u.id=s.user_id";
        fields =
            "s.id, s.title, u.full_name AS owner, s.category, IF(s.active,'Live','Inactive') AS"
                + " visibility";
        columns =
            columns(
                "id",
                "ID",
                "title",
                "Skill",
                "owner",
                "Teacher",
                "category",
                "Category",
                "visibility",
                "Visibility");
      }
      case "sessions" -> {
        from =
            " FROM session_requests s JOIN users l ON l.id=s.requester_id JOIN users t ON"
                + " t.id=s.provider_id JOIN skills k ON k.id=s.skill_id";
        fields =
            "s.id, k.title, l.full_name AS learner, t.full_name AS teacher, s.starts_at AS"
                + " scheduled, s.duration_minutes AS minutes, s.status, s.issue";
        columns =
            columns(
                "id",
                "ID",
                "title",
                "Skill",
                "learner",
                "Learner",
                "teacher",
                "Teacher",
                "scheduled",
                "Start (IST)",
                "minutes",
                "Minutes",
                "status",
                "Status",
                "issue",
                "Reported issue");
      }
      case "ledger" -> {
        from =
            " FROM credit_transactions s LEFT JOIN users f ON f.id=s.sender_id JOIN users t ON"
                + " t.id=s.receiver_id";
        fields =
            "s.id, COALESCE(f.full_name,'CredLearn') AS sender, t.full_name AS receiver, s.amount,"
                + " s.transaction_type AS kind, s.session_id AS session, s.created_at AS recorded";
        columns =
            columns(
                "id",
                "ID",
                "sender",
                "From",
                "receiver",
                "To",
                "amount",
                "Credits",
                "kind",
                "Type",
                "session",
                "Session",
                "recorded",
                "Recorded (IST)");
      }
      default -> {
        from = " FROM users s LEFT JOIN credit_wallets w ON w.user_id=s.id";
        fields =
            "s.id, s.full_name AS name, s.email, s.department, s.year_of_study AS year, s.role,"
                + " w.balance, w.reserved, s.created_at AS joined";
        columns =
            columns(
                "id",
                "ID",
                "name",
                "Name",
                "email",
                "Email",
                "department",
                "Department",
                "year",
                "Year",
                "role",
                "Role",
                "balance",
                "Balance",
                "reserved",
                "Reserved",
                "joined",
                "Joined (IST)");
        if (!q.isEmpty()) {
          from +=
              " WHERE LOCATE(LOWER(?), LOWER(s.full_name)) > 0 OR LOCATE(LOWER(?), LOWER(s.email))"
                  + " > 0";
          parameters.add(q);
          parameters.add(q);
        }
      }
    }
    long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, parameters.toArray());
    int pages = Math.max(1, (int) Math.ceil(total / 20.0));
    int page = Math.max(0, Math.min(requestedPage, pages - 1));
    parameters.add(20);
    parameters.add(page * 20);
    var rows =
        jdbc.queryForList(
            "SELECT " + fields + from + " ORDER BY s.id DESC LIMIT ? OFFSET ?",
            parameters.toArray());
    rows.forEach(
        row ->
            row.replaceAll(
                (key, value) ->
                    value instanceof Timestamp time
                        ? display.time(time.toInstant())
                        : value == null ? "—" : value));
    return new Table(view, q, columns, rows, total, page, pages);
  }

  private List<Column> columns(String... values) {
    List<Column> result = new ArrayList<>();
    for (int i = 0; i < values.length; i += 2) result.add(new Column(values[i], values[i + 1]));
    return result;
  }
}
