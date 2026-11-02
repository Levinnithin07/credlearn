# CredLearn

<img src="src/main/resources/static/brand/credlearn-wordmark.png" alt="CredLearn" width="300">

**Teach Skills. Earn Credits. Keep Learning.**

**CredLearn: A Time-Credit-Based Peer-to-Peer Skill Exchange Platform for Campus Communities**

CredLearn connects students who want to teach and learn from one another. One
hour of teaching earns one credit, and one hour of learning spends one credit.
This repository contains the runnable application, source code, database schema
migrations, frontend assets, and automated tests. It starts with an empty database;
no accounts, administrator credentials, production data, or Google credentials are
included.

## Features

- Registration, login, logout, BCrypt password hashing, and CSRF protection.
- Student profiles, learning interests, and skill creation, editing, and search.
- Session requests, acceptance, scheduling, cancellation, and issue reporting.
- Three starter credits per registration, reservations on acceptance, and an
  auditable credit ledger. Transfers require both completion confirmations.
- Participant-only chat with persistent history and unread indicators.
- Google Calendar OAuth integration for genuine scheduled Google Meet links.
- A protected administrator dashboard with read-only user, skill, session, and
  ledger views. Administrators cannot read other participants' private messages.
- Responsive Thymeleaf pages with locally included Bootstrap and branding assets.

## Requirements

| Requirement | Version / purpose |
| --- | --- |
| Java JDK | **21**; `JAVA_HOME` and `java` must point to it |
| MySQL Server | **8.0.18+** or **8.4**; the database server must be running |
| Git | Clone the repository |
| Maven | Wrapper included; it downloads Maven **3.9.11** on first use |
| Internet access | Initial Maven/dependency downloads; Google OAuth if enabled |
| Browser | A current browser with JavaScript enabled |
| PowerShell | **7+** for the optional Windows convenience scripts |
| Google Cloud OAuth web client | Optional; required only for Google Meet creation |

The backend uses Spring Boot 3.5.16, Spring Security, Spring Data JPA/Hibernate,
Flyway, and MySQL. The frontend uses Thymeleaf, Bootstrap 5, and JavaScript.
Node.js, npm, and a frontend build step are **not required**.

## 1. Clone the project

```text
git clone https://github.com/Levinnithin07/credlearn.git
cd credlearn
java -version
```

Check that Java reports version 21. On macOS/Linux, make the wrapper executable
if your checkout did not preserve its executable flag:

```bash
chmod +x mvnw
```

## 2. Create your local MySQL databases

Open MySQL Workbench or the MySQL command-line client and connect using your own
local database administrator account. Run the following **once on a new local
development server**, not on a shared or production server:

```sql
CREATE DATABASE skillswap
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE skillswap_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'credlearn_local'@'localhost' IDENTIFIED BY RANDOM PASSWORD;
GRANT ALL PRIVILEGES ON skillswap.* TO 'credlearn_local'@'localhost';
GRANT ALL PRIVILEGES ON skillswap_test.* TO 'credlearn_local'@'localhost';
```

MySQL returns a newly generated password for this developer account. Keep that
value privately and enter it at the password prompt in the next step. Nothing in
this repository supplies a working database password. The schema names retain
the project's original internal naming; the application is branded CredLearn.

The local account has schema-scoped privileges for Flyway migrations and tests.
Production should use a separate migration account and a restricted runtime
account instead; see [hosting configuration](docs/HOSTING.md).

You do not need to import a database dump. Flyway creates the tables from the
versioned migrations at the first application startup. Hibernate validates the
schema rather than recreating it.

## 3. Set environment variables and start

`.env.example` documents the settings and contains blank credential fields.
**Spring Boot does not automatically load `.env` files.** Set values in the same
terminal used to start the application, or configure your IDE's run environment.
Your database credentials differ from the student accounts created in the website.

### Windows — PowerShell 7

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/skillswap?connectionTimeZone=UTC'
$env:DB_USERNAME='credlearn_local'
$env:DB_PASSWORD=Read-Host 'Your generated local database password' -MaskInput
$env:PORT='8080'
./mvnw.cmd spring-boot:run
```

If MySQL uses a different port, change `3306` in `DB_URL`.

### macOS / Linux — Bash

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/skillswap?connectionTimeZone=UTC'
export DB_USERNAME='credlearn_local'
read -rs -p 'Your generated local database password: ' DB_PASSWORD
echo
export DB_PASSWORD
export PORT=8080
./mvnw spring-boot:run
```

Wait for the Spring Boot startup message, then open **http://127.0.0.1:8080**.
Register your own student account. Google configuration is optional: login,
skills, chat, and credits work without it, while meeting generation shows a
configuration-pending message.

### Optional isolated MySQL helper on Windows

If MySQL 8.0 is installed at the default Windows path and you prefer a separate
development instance, run these commands in PowerShell 7 from the project root:

```powershell
./scripts/start-local-db.ps1
./scripts/run-local.ps1
```

This helper uses local port **3307**, generates random credentials, and stores
them with the local database files under ignored `.local/`. It leaves an existing
listener on that port unchanged. This is an alternative to the manual MySQL setup
above; it is not a production database service. Stop the app with Ctrl+C, then
use `./scripts/stop-local-db.ps1` to stop this isolated database.

## Configuration reference

| Variable | Purpose | Default / requirement |
| --- | --- | --- |
| `DB_URL` | JDBC connection string | Local `skillswap` database on port 3307; set it explicitly for another server |
| `DB_USERNAME` | Your MySQL account | Legacy local-helper username; set your own for manual setup |
| `DB_PASSWORD` | Your MySQL account password | Required; no password is provided |
| `SERVER_ADDRESS` | Application bind address | `127.0.0.1` locally |
| `PORT` | HTTP listening port | `8080` |
| `COOKIE_SECURE` | Restrict session cookies to HTTPS | `false` locally; hosted profile enforces `true` |
| `GOOGLE_CLIENT_ID` | Your Google OAuth web client ID | Optional; blank until configured |
| `GOOGLE_CLIENT_SECRET` | Your OAuth web client secret | Optional; never commit it |
| `GOOGLE_REDIRECT_URI` | Exact registered Google callback | `http://127.0.0.1:8080/google/callback` |
| `SPRING_PROFILES_ACTIVE` | Runtime profile | Default for local use; `hosted` or `vercel` for configured hosting |
| `CREDLEARN_SESSION_KEY` | Shared-session AES encryption key | Required only with `vercel`; base64 of 32 random bytes |

## Google Meet setup (optional)

1. In your own Google Cloud project, enable the **Google Calendar API**.
2. Configure an OAuth consent screen and add your testing Google accounts.
3. Create an **OAuth 2.0 Web Application** client with authorized redirect URI
   `http://127.0.0.1:8080/google/callback`.
4. Configure `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI`
   privately, then restart CredLearn.
5. Create and accept a future session using two student accounts. One participant
   connects Google and selects **Generate Google Meet Link**.

Only the meeting organizer needs to authorize Google. The API creates a scheduled
event on that account's primary calendar. Both participants see the same returned
Meet link. No meeting URL is fabricated. Tests use simulated API responses;
verify a genuine link with your own configured Google account.

See [complete Google setup and troubleshooting](docs/GOOGLE-SETUP.md).

## Build a runnable JAR

Keep the database environment variables set in your terminal. For Windows:

```powershell
./mvnw.cmd -DskipTests package
java -jar target/credlearn-0.1.0-SNAPSHOT.jar
```

On macOS/Linux, replace `./mvnw.cmd` with `./mvnw`. Run the tests below separately
before releasing; `-DskipTests` packages the app without executing them.

## Run the automated tests

**Use only the disposable `skillswap_test` database. Tests delete its records.**
They never need Google credentials or access to the application's live database.

Keep your local `DB_USERNAME` and `DB_PASSWORD` set, then run:

```powershell
# Windows PowerShell 7
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/skillswap_test?connectionTimeZone=UTC'
./mvnw.cmd verify
```

```bash
# macOS / Linux Bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/skillswap_test?connectionTimeZone=UTC'
./mvnw verify
```

If you used the optional isolated Windows helper, use
`./scripts/run-local.ps1 -Test` instead; it selects its test database on port 3307.

The suite covers authentication, CSRF, authorization, skill ownership, session
transitions, scheduling, private chat, Google API contracts, wallet reservations,
concurrent credit transfers, and rollback. It also checks encrypted shared
sessions across independent HTTP servers and a restarted instance. These are
real-MySQL integration tests, not an in-memory database substitute.

After testing, set `DB_URL` back to your development database before starting the
application normally.

## Try the student workflow

1. Register two accounts and sign in using separate browser profiles.
2. One student publishes a skill; the other requests a future learning session.
3. The provider accepts. Credits are reserved, and private chat becomes available.
4. Agree on the schedule and optionally create a Google Meet link.
5. After the scheduled end, both students confirm completion. Credits transfer
   exactly once, and the ledger records the exchange.

Sessions last 30–240 minutes in 15-minute increments. All displayed scheduling
times use **Asia/Kolkata (IST)**; database timestamps use UTC. Acceptance and
chatting do not transfer credits. See [credit rules](docs/CREDIT-WORKFLOW.md).

## Administrator setup

There is **no default administrator login**. Register an account normally, then
promote that account using your trusted local database administrator connection.
Use the actual account's ID from your own `users` table:

```sql
SELECT id, full_name, email, role FROM users ORDER BY id;
```

Run `UPDATE users SET role = 'ADMIN' WHERE id = ...` with that specific ID in
your own database, then sign in at `/admin/login`. Never expose role assignment
through public registration. No production administrator identity, password, or
password hash is included in this repository.

## Repository layout

```text
src/main/java/com/credlearn/     Controllers, services, entities, repositories, security
src/main/resources/templates/  Thymeleaf views
src/main/resources/static/     CSS, JavaScript, logo, Bootstrap assets
src/main/resources/db/migration/ Versioned Flyway migrations V1–V5
src/test/java/                 Unit and real-MySQL integration tests
scripts/                       Optional local helpers and container entrypoint
docs/                          Architecture, credit, OAuth, and hosting guides
.env.example                   Blank environment-variable reference
Dockerfile                     Java 21 container build
vercel.json                    Generic container runtime configuration
```

## Hosting and security

The repository contains no linked hosting project, live database address, cloud
credentials, private records, SQL backup, or OAuth client JSON. Local secrets,
certificates, logs, generated binaries, and provider state are excluded by
`.gitignore`. The only committed SQL files are schema migrations.

For deployment, configure your own database and server-side secrets. The
`hosted` profile uses HTTPS-aware settings; `vercel` additionally uses encrypted
JDBC sessions and three-second chat polling across instances. Default/local chat
uses SSE and requires one process unless an external event bus is added.
See [hosting configuration](docs/HOSTING.md) and [security notes](SECURITY.md).

Current limitations include unverified email ownership/campus enrollment, no
password-reset email flow, no automatic dispute arbitration, and no chat-file
sharing. Google Calendar events must be cancelled by their organizer in Google
Calendar. Check your host's current quotas, cold starts, and database availability
before making availability commitments.

## Troubleshooting

- **Java version error:** install JDK 21 and check `JAVA_HOME` / `java -version`.
- **Database connection refused:** start MySQL and check the host and port in `DB_URL`.
- **Access denied:** use your database password, not your CredLearn website password.
- **Missing `DB_PASSWORD`:** export it in the terminal before running Maven or Java.
- **Flyway permission error:** use a schema-scoped development/migration account.
- **Port occupied:** set `PORT` to another unused port before starting.
- **`redirect_uri_mismatch`:** match the exact registered callback; do not mix
  `localhost` and `127.0.0.1` during login/OAuth.
- **PowerShell blocks scripts:** use an approved PowerShell 7 setup or the manual
  environment-variable and Maven commands above; no permanent policy change is needed.

Bootstrap's license is included under `src/main/resources/static/vendor/`.
