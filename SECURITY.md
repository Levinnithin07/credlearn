# Security and private configuration

This repository intentionally contains no working account credentials, database
data, OAuth client secrets, private keys, provider tokens, or password hashes.
An empty install has no seeded student or administrator accounts.

Administration is owner-bound. The deployment's private owner account binding
and the current persisted administrator role must both match; `ADMIN` in a table
or an old login session is not enough. Public registration never grants admin
rights. Admin routes and backend admin services recheck the binding and stored
role on every request. Missing owner configuration disables all admin access.
Keep database, hosting, and repository management access restricted to the owner:
someone who controls the server or database can alter code, account credentials,
or deployment settings, which application authorization cannot prevent.

- Keep real environment variables in your shell, IDE secret settings, or hosting
  secret manager. `.env.example` is a blank reference, not an automatically loaded
  configuration file.
- Keep downloaded Google OAuth JSON and database certificates outside Git.
  `.local/`, `.env*` (except `.env.example`), key/certificate files, SQL exports,
  database directories, and provider configuration are ignored.
- The SQL exceptions in `.gitignore` apply only to versioned schema migrations.
  Do not add personal records, account inserts, or credentials to migrations.
- `gitignore` does not remove an already tracked secret. If a real secret is ever
  committed, revoke/rotate it, remove it from every affected Git revision, and
  review exposure. Do not copy private repository history into this public repo.
- Configure HTTPS and verified database TLS in hosting. Use schema-scoped
  migration permissions and a restricted runtime database account.
- Keep one stable, private 32-byte AES key across instances when using the
  `vercel` profile. Changing it invalidates existing encrypted sessions.
- Use only a disposable `skillswap_test` schema for tests; test cleanup deletes
  its records. Test-only sample identities and simulated API tokens are not
  credentials for any real service.
- Never paste passwords, OAuth codes, refresh tokens, private chat messages, or
  user database dumps into public issues or logs.

For a sensitive vulnerability, use GitHub's private vulnerability-reporting flow
when available, rather than posting exploit details or user data in a public issue.
