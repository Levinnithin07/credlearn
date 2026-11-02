# Architecture

CredLearn is a Java 21 / Spring Boot application with server-rendered Thymeleaf
pages, locally included Bootstrap, and a persistent MySQL database.

```text
Browser (Thymeleaf, Bootstrap, JavaScript)
  -> Spring MVC controllers and Spring Security
  -> transactional application services
  -> Spring Data JPA repositories
  -> MySQL constraints, indexes, and row locks
```

## Boundaries

- Controllers validate forms and API requests and obtain the current user from
  the authenticated principal. Browser-provided identifiers are not authority.
- Services enforce ownership, participant access, session state, reservations,
  scheduling conflicts, and completion rules inside transaction boundaries.
- JPA repositories provide persistence and pessimistic locks. Both wallets are
  locked in stable ID order to reduce deadlocks during acceptance/settlement.
- Flyway owns the schema migrations. Hibernate validates it at startup.
- Spring Security provides BCrypt passwords, session authentication, role checks,
  CSRF protection, security headers, and login/logout.

## Modules

| Area | Main services |
| --- | --- |
| Accounts and profiles | `UserService`, `StudentDetailsService` |
| Skill marketplace | `SkillService` |
| Session lifecycle and credits | `SessionService`, `OverviewService` |
| Agreed schedule changes | `ScheduleService` |
| Private chat | `ChatService`, `ChatEvents` |
| Google OAuth and meeting creation | `GoogleConnection`, `GoogleCalendarGateway`, `MeetingService` |
| Read-only administration | `AdminQueryService` |

## Credits and concurrency

An accepted session reserves the learner's credits. Both participants must
confirm completion after the scheduled end before one atomic transaction debits
the learner, releases the reservation, credits the provider, records the unique
ledger entry, and completes the session. No partial settlement is committed if
the operation fails. See [credit workflow](CREDIT-WORKFLOW.md).

## Chat and meetings

Submitting a request plus provider acceptance activates one conversation.
Pending/declined sessions have no chat access. Only the participants can retrieve
or send messages. Accepted/disputed conversations permit text; completed or
previously accepted cancelled conversations retain read-only history. Messages
are plain text, bounded in length, paginated, and rendered with `textContent`.
Client UUIDs deduplicate retries, and server-side limits control message volume.

The default transport uses SSE change signals followed by authorized retrieval.
Vercel uses three-second authenticated polling so updates do not depend on which
container receives a request. The Vercel profile also persists encrypted HTTP
sessions. See [hosting configuration](HOSTING.md).

Google authorization uses state and PKCE independently of the CredLearn login.
Calendar API meeting creation uses a saved event identifier and bounded creation
lease to reconcile retries without duplicate meetings. Accepting requests,
rescheduling, chatting, and creating a meeting do not transfer credits.

## Data and credentials

Only versioned schema migrations are included. There are no account seeds,
password hashes, chat exports, wallet records, OAuth credentials, or linked cloud
accounts in this repository. Fresh installations create their own data through
registration and normal workflows. See [security notes](../SECURITY.md).
