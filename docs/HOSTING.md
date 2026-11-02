# Hosting configuration

This source copy has no connection to an existing deployment, database service,
hosting account, or Google OAuth client. Provision and configure your own
resources. Deployment files contain runtime configuration, not account access.

## Standard Java / container host

Use Java 21 and a persistent MySQL database. The provided Dockerfile builds the
runnable application and runs it as a non-root user with the `hosted` profile.

Configure server-side environment values:

| Variable | Required value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `hosted` for a single-instance container host |
| `PORT` | Port supplied by your host; container example uses 10000 |
| `DB_URL` | Your own JDBC URL with `connectionTimeZone=UTC` and verified TLS |
| `DB_USERNAME`, `DB_PASSWORD` | Your restricted application database account |
| `MIGRATION_DB_USERNAME`, `MIGRATION_DB_PASSWORD` | Schema-scoped account for Flyway when startup migrations are enabled |
| `DB_CA_CERT_BASE64` | Base64 of your database provider's public PEM CA certificate |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Your OAuth web client, if Google Meet is enabled |
| `GOOGLE_REDIRECT_URI` | Your registered HTTPS callback |
| `CREDLEARN_ADMIN_OWNER_USER_ID` | Owner-managed private account binding; unset/zero denies all administrator access |

The container entrypoint imports the public database CA into
`/tmp/credlearn-db-trust.p12`. For that entrypoint, add these JDBC URL parameters:

```text
sslMode=VERIFY_IDENTITY
trustCertificateKeyStoreUrl=file:/tmp/credlearn-db-trust.p12
trustCertificateKeyStorePassword=changeit
trustCertificateKeyStoreType=PKCS12
```

`changeit` is the password for a temporary truststore containing only a public
certificate; it is not a database password or account credential. Never put the
actual database password in the JDBC URL or image layers. Hosted settings use
secure cookies and forwarded HTTPS headers; expose the app through a trusted
HTTPS reverse proxy.

Use a migration account only on this application's schema. The runtime account
needs ordinary DML on domain tables, only SELECT/INSERT on the append-only credit
ledger, and SELECT on Flyway history when migrations are managed separately.
Back up existing records before schema migrations; do not reset the database.

## Vercel container profile

`vercel.json` selects the container runtime and region `bom1`; adapt region and
host settings to your own deployment. Check the platform's current availability,
pricing and quotas rather than assuming any hosting tier stays unchanged.

1. Create/link your own project. Keep `.vercel/` private and untracked.
2. Back up your database and apply Flyway migrations V1–V5 using a trusted
   migration process before starting the runtime service.
3. Give the runtime database account SELECT, INSERT, UPDATE and DELETE on
   `credlearn_http_sessions` and `credlearn_http_sessions_ATTRIBUTES` in addition
   to its domain-table permissions.
4. Set `SPRING_PROFILES_ACTIVE=vercel`, `PORT=10000`, and
   `SPRING_FLYWAY_ENABLED=false` after the controlled migration.
5. Set `CREDLEARN_SESSION_KEY` to a privately generated base64 encoding of exactly
   32 cryptographically random bytes. Keep it stable across all instances and
   deployments, and store it as a server-only Secret.
6. Configure the database CA, database credentials, and optional Google OAuth
   variables from the preceding table. Migration-account credentials are not
   needed in Vercel when migrations are managed outside the runtime.
7. Test a staged deployment before promoting its public domain. Register your
   actual public Google callback and verify a real meeting after release.

The Vercel profile uses JDBC-backed HTTP sessions with AES-256-GCM authenticated
encryption for every attribute, including Google tokens and Spring Security
state. It uses short authenticated chat polling every three seconds while the
chat tab is visible. This avoids an in-memory SSE registry across instances.
Cookies remain HttpOnly, Secure and SameSite=Lax; session inactivity timeout is
30 minutes. Changing the encryption key invalidates existing session contents.

The default/hosted profile instead uses in-memory login sessions and SSE, suitable
for a single process. Multi-process SSE requires an external event bus; do not
assume that an in-memory event registry is shared between containers.

## Release checks

- Run `verify` only against a disposable local test database.
- Confirm HTTPS, secure cookies, CSRF protection, and anonymous access rejection.
- Confirm only session participants can access chat and meeting controls.
- Test Google OAuth and genuine Meet creation with your own credentials.
- Confirm both completion acknowledgements are required and credits settle once.
- Arrange database backups, availability, retention, and monitoring appropriate
  to your deployment. Containers may restart or cold-start; the database must
  remain available independently of a developer laptop.
