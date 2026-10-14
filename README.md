# ApplyMate

Find jobs, see how well you match them, tailor your resume and cover letter for each one, and track every application until you hear back.

**Next.js 15** (App Router, TypeScript, Tailwind) · **Spring Boot 3** (Java 21, JPA, Security) · **PostgreSQL 16** (Flyway) · **Gemini** (free tier)

![CI](https://github.com/estee100/ApplyMate/actions/workflows/ci.yml/badge.svg)

Every external service has a free tier and no paid APIs are involved.

## Features

**Find jobs**
- **Live feed** from Adzuna (local jobs) and Himalayas (remote), searched in parallel and merged newest first, with duplicates across sources dropped. Filter by keywords, location, job type, remote only and date posted.
- **Recommended for you**: jobs ranked by how many of your resume's skills they mention. The best few get an AI match score.
- **Paste a link** to any posting, or **paste the text** for sites that block fetching. Or click the **"Add to ApplyMate" bookmarklet** on any job page.

**Match and tailor**
- **Match analysis**: a percentage, the requirements you meet and the ones you're missing, cached per job and resume version.
- **Master resume** with **switchable resume versions**. **Start from master** (an exact copy) or **tailor with AI**, which rewords and reorders but never changes employers, dates or titles.
- **Cover letters**, a structured editor with live preview, **Classic, Modern and Compact templates**, and PDF export.
- **ATS keyword check**: which of the advert's keywords your document uses. No AI involved.
- **Changes view**: a word-level diff of what the AI wrote against your edit.

**Track**
- **Board** with drag and drop plus a keyboard "Move to" menu. Every stage change goes on a timeline with a note.
- **Communications log**, next steps, follow-up flags, **interview prep**, and **AI email drafts** (follow-up, thank-you, withdraw, accept, decline). Drafts are never sent by the app.
- **Calendar** in the app, plus a **private iCal subscription** for Google Calendar or Outlook.
- **Analytics** (response, interview and offer rates, time to reply) and a **weekly goal with a streak**.
- **CSV import** from another tracker and **CSV export**.

**Everywhere**
- **Command palette (Ctrl/⌘K)** with full-text search over your own data.
- **Installable PWA** with an offline page, a first-run **onboarding tour**, light and dark themes, and a phone layout with a tab bar.
- Profile picture, password reset by email, rotating sessions, account deletion, opt-in reminders.

## Engineering highlights

The parts most worth reading, and where to find them.

| Area | What it does | Code |
| --- | --- | --- |
| **Sessions** | 15-minute access JWTs plus rotating refresh tokens with **reuse detection**: replaying a used refresh token revokes the whole token family (OAuth 2.0 Security BCP). Row locks serialise concurrent refreshes, and a 10-second grace window tells a two-tab race apart from theft. The client refreshes across tabs with the Web Locks API. | [`SessionService`](backend/src/main/java/com/applymate/service/SessionService.java), [`api.ts`](frontend/lib/api.ts) |
| **Distributed rate limiting** | A token bucket stored in Postgres and updated by **one atomic `INSERT … ON CONFLICT DO UPDATE … WHERE`**, so every instance shares the limits and concurrent requests can't overspend. Tested with 50 racing threads. Elapsed time is clamped so clock skew can't drain a bucket. | [`RateLimiter`](backend/src/main/java/com/applymate/service/RateLimiter.java) |
| **Transactional outbox** | Emails are inserted in the same transaction as the change that caused them, then delivered by a worker that claims rows with **`FOR UPDATE SKIP LOCKED`**, with exponential backoff and dead-lettering. Reminders use a dedupe key, so the job is idempotent across instances. | [`EmailOutbox`](backend/src/main/java/com/applymate/mail/EmailOutbox.java) |
| **Optimistic locking** | `@Version` on applications, documents and the master resume. A stale edit returns 409 instead of silently overwriting another tab's change; five concurrent writers produce exactly one winner. | [`Versions`](backend/src/main/java/com/applymate/error/Versions.java), [`OptimisticLockingTest`](backend/src/test/java/com/applymate/OptimisticLockingTest.java) |
| **Full-text search** | Generated, weighted `tsvector` columns with GIN indexes, prefix queries for search-as-you-type, `ts_rank_cd` ranking and `ts_headline` snippets returned as ranges rather than HTML. User input is sanitised before `to_tsquery`. | [`SearchService`](backend/src/main/java/com/applymate/service/SearchService.java), [`V9`](backend/src/main/resources/db/migration/V9__full_text_search.sql) |
| **LLM integration** | A provider seam (`LlmClient`) with a Gemini REST client. Response schemas are generated from Java records by reflection. Calls fall back through a model chain, skip models that are out of quota, retry 503s, and charge a per-user quota in Postgres that's refunded on failure. Tested against a **local fake Gemini server** over real HTTP. | [`ai/`](backend/src/main/java/com/applymate/ai), [`GeminiClientTest`](backend/src/test/java/com/applymate/ai/GeminiClientTest.java) |
| **Prompt-injection defence** | Job adverts are fenced as untrusted data and never reach the system prompt. Output is always structured and scores are clamped. Tailoring returns *edits keyed to the master's indexes*, applied to a copy, so facts can't change, entries can't gain bullets, and skills must already exist. | [`Prompts`](backend/src/main/java/com/applymate/ai/Prompts.java), [`ResumeTailoring`](backend/src/main/java/com/applymate/resume/ResumeTailoring.java) |
| **SSRF protection** | Pasted links must resolve to public addresses (loopback, private, link-local, CGNAT, ULA and IPv4-mapped forms are rejected). Redirects are followed by hand and re-validated at every hop, with caps on size and hops. | [`SafeFetcher`](backend/src/main/java/com/applymate/jobs/SafeFetcher.java) |
| **Hostile uploads** | Resumes are typed by magic bytes, and DOCX is parsed with XXE off and a decompression cap. Avatars have their dimensions checked **before decoding** (decompression bombs) and are re-encoded to strip EXIF and GPS data. | [`ResumeTextExtractor`](backend/src/main/java/com/applymate/resume/ResumeTextExtractor.java), [`AvatarService`](backend/src/main/java/com/applymate/service/AvatarService.java) |
| **Observability** | Actuator and Prometheus on a private management port. Per-model AI latency and outcome, job-source timings, feed cache hit rate and outbox depth are all exported. A request ID appears on every log line and comes back with errors. | [`RequestIdFilter`](backend/src/main/java/com/applymate/config/RequestIdFilter.java), [`application.yml`](backend/src/main/resources/application.yml) |
| **Standards work** | RFC 5545 iCalendar (escaping, 75-octet folding without splitting UTF-8), RFC 4180 CSV with formula-injection protection, OpenAPI docs. | [`ICalendar`](backend/src/main/java/com/applymate/util/ICalendar.java), [`Csv`](backend/src/main/java/com/applymate/util/Csv.java) |
| **Frontend** | Typed API client, an accessible dialog (focus trap, focus return), an ARIA combobox command palette, an LCS diff, an SVG-mask spotlight tour, and a service worker that never caches personal data. | [`components/`](frontend/components), [`lib/diff.ts`](frontend/lib/diff.ts), [`sw.js`](frontend/public/sw.js) |

## Architecture

```
 Browser (Next.js PWA)                 Spring Boot API (:8080/api)                  PostgreSQL
 ┌─────────────────────┐   JWT    ┌───────────────────────────────────────┐   ┌──────────────────────┐
 │ App Router pages     │ ──────▶ │ Security: JWT filter, refresh rotation │   │ users, sessions      │
 │ typed API client     │         │ Controllers → services                 │──▶│ jobs (+ tsvector)    │
 │ cross-tab refresh    │ ◀────── │ RateLimiter ─ token buckets ──────────▶│   │ applications, events │
 │ service worker       │  JSON   │ EmailOutbox ─ SKIP LOCKED worker ─────▶│   │ documents, matches   │
 └─────────────────────┘         │ LlmClient ─▶ Gemini (model fallback)   │   │ outbox, rate buckets │
                                  │ JobFeed ─▶ Adzuna + Himalayas (cached) │   └──────────────────────┘
        Prometheus ─────────────▶ │ Actuator (:9091, private)              │        Flyway V1–V13
                                  └───────────────────────────────────────┘
```

## Running locally

### Option A: Docker Compose

```bash
export JWT_SECRET="a-long-random-string-of-at-least-32-characters"
export GEMINI_API_KEY="..."                          # optional, enables AI features
export ADZUNA_APP_ID="..." ADZUNA_APP_KEY="..."      # optional, adds local jobs
docker compose up --build
```

- App: http://localhost:3000
- API: http://localhost:8000/api. Swagger UI is at `/api/swagger-ui.html`.
- Health and metrics: port 9091 inside the backend container (deliberately not published).

### Option B: Run each service

```bash
docker compose up -d postgres

cd backend
cp application-local.yml.example application-local.yml   # add your keys
./gradlew bootRun                                         # :8080, metrics on :9091

cd ../frontend
npm install && cp .env.example .env.local
npm run dev                                               # :3000
```

### Keys

| Variable | What it's for | Without it |
| --- | --- | --- |
| `GEMINI_API_KEY` | Match analysis, skill extraction, tailoring, cover letters, interview prep, email drafts | AI features are off; everything else works. Free key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey). |
| `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` | Local jobs in the feed | The feed shows remote jobs only. Free key at [developer.adzuna.com](https://developer.adzuna.com). |
| `SMTP_*`, `MAIL_ENABLED=true` | Password reset and reminder emails | No email is sent. `LOG_RESET_LINKS=true` prints reset links locally. |
| `JWT_SECRET` | Signing access tokens | A dev default is used, with a warning. **Always set it in production.** |
| `PUBLIC_API_URL`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | Deployed URLs | Default to localhost. |
| `AI_DAILY_LIMIT_PER_USER` | Per-user AI cap | 25 a day. |

## Testing

```bash
cd backend && ./gradlew test                       # 113 tests; needs Docker (Testcontainers Postgres)
cd frontend && npm test                            # Vitest unit tests
cd frontend && E2E_BASE_URL=http://localhost:3000 npx playwright test   # against a running stack
```

- **Backend:** integration tests run against real Postgres, including concurrency tests (racing refreshes, buckets, outbox workers and optimistic locks), injection and SSRF cases, and the Gemini client over HTTP against a fake server. The model and job sources are mocked, so nothing touches the network.
- **Frontend:** Vitest covers the diff algorithm, date handling, the open-redirect guard and fuzzy ranking.
- **End-to-end:** Playwright runs in CI against Postgres, the built jar and a production frontend build. It covers signing up, the tour, logging and moving applications, search, saving jobs, themes, sign-out and the phone layout.

CI runs all three on every push.

## Known limits

- **DNS rebinding:** the JDK client resolves the host again when it connects, so a near-zero-TTL rebinding attack could still race the SSRF check. Closing that fully needs an egress proxy.
- **Feed cache:** it's per instance. That's harmless, since it only saves Adzuna quota. Rate limits, quotas, sessions and email are all shared through Postgres.
- **Token storage:** access and refresh tokens live in `localStorage`. That keeps the API stateless and CORS simple, but any XSS could read them. React escapes all output and the app never injects HTML. An httpOnly cookie with CSRF protection would close the gap.
- **Delivery semantics:** email delivery is at-least-once. A crash after SMTP accepts a message but before it's marked sent can resend it.
- **Scanned PDFs:** image-only PDFs have no text to extract.

## API

All endpoints except `/auth/*`, `/health`, the calendar feed and the API docs need `Authorization: Bearer <token>`. Errors are always `{"error": "..."}`. The full reference is at `/api/swagger-ui.html`.

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password`, `/change-password` |
| Profile | `GET/PATCH/DELETE /profile`; `GET/POST/DELETE /profile/avatar`; `POST/DELETE /profile/calendar-feed` |
| Resume | `GET/POST /resumes`, `POST /resumes/{id}/activate`, `PUT /resumes/active/skill-profile`, `POST …/refresh`, `GET/PUT /resumes/active/master`, `POST …/master/fill` |
| Jobs | `GET /jobs/feed`, `/recommended`, `/mine`, `/saved`, `/{id}`; `POST /jobs/paste-link`, `/paste-text`, `/{id}/match`; `PUT/DELETE /jobs/{id}/saved` |
| Documents | `POST /jobs/{id}/documents/from-master`, `/resume`, `/cover-letter`; `GET/PUT/DELETE /documents/{id}`; `PUT /documents/{id}/template`; `GET /documents/{id}/keywords`; `POST /documents/{id}/reset` |
| Applications | `GET/POST /applications`, `GET /applications/stats`, `/progress`, `/export.csv`; `POST /applications/import`; `GET/PATCH/DELETE /applications/{id}`; `GET …/events`; `GET/POST …/communications`; `GET/POST …/interview-prep`; `POST …/email-draft` |
| Views | `GET /search?q=`, `/history`, `/calendar?from&to`, `/analytics/summary`, `/calendar/feed/{token}.ics` |
