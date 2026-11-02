# Configure your own Google Meet integration

Google Meet creation is optional. Normal registration, skills, sessions, private
chat, and credit transfers work without Google credentials.

## Google Cloud setup

1. Create or select your own project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable **Google Calendar API**. This application creates Calendar events with
   Meet conference data; it does not use a fabricated or hardcoded meeting link.
3. Configure Google Auth Platform / the OAuth consent screen. Use an appropriate
   audience for your accounts, and add authorized testing accounts while in
   Testing mode.
4. Configure the permissions used by the application:
   - `openid`
   - `https://www.googleapis.com/auth/calendar.events.owned`
5. Create an **OAuth 2.0 Web Application** client.
6. Add `http://127.0.0.1:8080/google/callback` as an authorized redirect URI.
   Scheme, host, port, and path must exactly match `GOOGLE_REDIRECT_URI`.
7. Store the client credentials outside the repository or in a secret manager.
   No Google credentials are included in this repository.

The Calendar permission can manage events on calendars owned by the consenting
account. Explain it accurately on your consent screen. The application operates
on its own saved event identifiers and does not send invitation emails or add
attendees automatically. Only the participant who organizes the meeting needs
to connect Google; the other participant joins using the shared link.

## Local environment

In PowerShell 7, from the project root with your database environment configured:

```powershell
$env:GOOGLE_CLIENT_ID=Read-Host 'Your OAuth web client ID'
$env:GOOGLE_CLIENT_SECRET=Read-Host 'Your OAuth web client secret' -MaskInput
$env:GOOGLE_REDIRECT_URI='http://127.0.0.1:8080/google/callback'
./mvnw.cmd spring-boot:run
```

Use `http://127.0.0.1:8080` consistently throughout CredLearn login and the Google
authorization flow. If you choose `localhost`, register and configure that exact
callback instead. Browser login cookies are host-specific.

## Verify a genuine meeting

1. Register two student accounts in your development database.
2. Publish a skill, request a future session, and have the provider accept it.
3. Resolve any pending schedule-change proposal through both participants.
4. One participant selects **Connect Google account** and grants permission.
5. Select **Generate Google Meet Link**. If conference creation is pending, use
   the offered check/retry action after a moment.
6. Verify the returned Google Meet URL in the organizer's Google Calendar and
   open it. Confirm both participants see the same link.
7. Check that retrying does not create duplicate active meetings and that credits
   remain reserved until both post-session completion confirmations.

The automated Google tests use simulated responses. Only your live authorization
and a genuinely returned, verified meeting link establish that your integration
is configured correctly.

## Token lifecycle and troubleshooting

- The authorization flow uses single-use state, a ten-minute deadline, and PKCE.
- Google tokens stay in the server-side login session. In the default/hosted
  profile, restarting the process discards them. The `vercel` profile stores the
  session attributes encrypted in MySQL, allowing them to survive container
  restarts until logout, disconnect, or session expiry.
- Tokens are never sent to browser storage or committed as application config.
- Expired/revoked Google permission requires reconnecting. Testing-mode consent
  and organization policies can also limit authorization lifetimes or access.
- `redirect_uri_mismatch`: compare the exact configured and registered callback.
- API/permission errors: check Calendar API enablement, granted scopes, test-user
  membership, and account eligibility for Google Meet.
- A creation timeout can be retried from the same CredLearn and Google account;
  the application reuses its saved event identifier and creation lease.
- The schedule locks when meeting creation starts. Cancelling in CredLearn does
  not delete the external Calendar event; its organizer must cancel it in Google
  Calendar. Joining opens Google Meet in another tab.

## Hosting

Register your own HTTPS callback `https://YOUR_HOST/google/callback` and set the
same value in the hosting environment. Keep localhost callbacks for development.
Store the client secret as a server-only secret, configure your own consent-screen
links, and meet Google's applicable publishing/verification requirements for the
audience you intend to support. See [hosting configuration](HOSTING.md).

Official references: [Calendar event insertion](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert),
[Google OAuth web-server flow](https://developers.google.com/identity/protocols/oauth2/web-server).
