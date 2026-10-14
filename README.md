# ApplyMate

Full-stack job application tracker: **Next.js** (App Router, TypeScript, Tailwind) + **Spring Boot** (REST, JPA, Security, JWT) + **PostgreSQL** (Flyway).

## Project layout

```
ApplyMate/
├── docker-compose.yml          # Postgres + backend + frontend
├── backend/                    # Spring Boot (Maven)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/applymate/
│       ├── controller/         # REST endpoints
│       ├── service/
│       ├── repository/
│       ├── model/              # JPA entities
│       ├── dto/
│       └── config/             # Security, JWT
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/       # Flyway
└── frontend/                   # Next.js
    ├── app/                    # App Router pages
    ├── components/
    └── services/api.ts         # API client + JWT
```

## API (base URL `/api`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Sign up (returns JWT) |
| POST | `/auth/login` | Login (returns JWT) |
| GET | `/applications` | List current user’s applications (optional `?status=APPLIED`) |
| GET | `/applications/{id}` | Get one |
| POST | `/applications` | Create |
| PUT | `/applications/{id}` | Update |
| DELETE | `/applications/{id}` | Delete |
| GET | `/analytics/summary` | Aggregated counts by status + per month (current user) |

All application and analytics endpoints require `Authorization: Bearer <token>`.

## Database

- **users** — `id`, `email`, `password_hash`, `created_at`
- **job_applications** — `id`, `user_id`, `job_title`, `company_name`, `location`, `job_posting_url`, `status`, `date_applied`, `notes`, timestamps

Status enum: `APPLIED`, `INTERVIEWING`, `OFFER`, `REJECTED`, `WITHDRAWN`.

## Local development

**Secrets:** Only `application-local.yml.example` belongs in git. Copy it to `backend/application-local.yml` (gitignored at repo root and under `backend/`) and set your JWT there or via `JWT_SECRET`. If `application-local.yml` was ever committed, run `git rm --cached backend/application-local.yml` and rotate any exposed secrets.

### Option A: Docker Compose

```bash
cd ApplyMate
# Set a strong JWT secret in production
export JWT_SECRET="your-256-bit-secret-string-here"
docker compose up --build
```

- Frontend: http://localhost:3000  
- Backend: http://localhost:8000/api  
- Postgres: localhost:5432 (user/password/db: `applymate`)

Frontend calls the API at `http://localhost:8000/api` (set in `docker-compose.yml` as `NEXT_PUBLIC_API_URL`). Rebuild the frontend image if you change the API URL.

### Option B: Run services locally

1. **PostgreSQL** — create database `applymate` and user, or run only the DB container:

   ```bash
   docker compose up postgres -d
   ```

2. **Backend** — from `backend/`:

   **Maven:** `mvn spring-boot:run`  
   **Gradle:** `gradle bootRun` (or `./gradlew bootRun` after generating the wrapper — see below)

   Configure `application.yml` or env: `SPRING_DATASOURCE_*`, `JWT_SECRET`.

   The backend supports **both** Maven (`pom.xml`) and Gradle (`build.gradle.kts`). Docker builds use **Gradle** by default. To add the Gradle wrapper without installing Gradle:  
   `docker run --rm -v "$(pwd):/app" -w /app gradle:8.11-jdk21-alpine gradle wrapper --gradle-version=8.11`

3. **Frontend** — from `frontend/`:

   ```bash
   npm install
   echo NEXT_PUBLIC_API_URL=http://localhost:8080/api > .env.local
   npm run dev
   ```

## Future extensibility

- **Interview tracking** — add `interviews` table + FK to `job_applications`; new REST module.
- **Analytics** — aggregate queries + optional `analytics` service layer.
- **Reminders** — scheduled jobs (Spring `@Scheduled` or Quartz) + `reminders` table.
- **Resume/cover letter storage** — `documents` table + file storage (S3/local) + upload endpoints.

Keep new features in separate packages or modules under the same controller/service/repository/dto structure.

## Tech stack

- Frontend: Next.js 15, React 19, TypeScript, Tailwind CSS, App Router
- Backend: Java 21, Spring Boot 3.4, Spring Web, Data JPA, Security, Flyway
- DB: PostgreSQL 16
