# Settlement Hub — Merchant Onboarding & Settlement Backend

> Design document for a Spring Boot backend. This file is the single source of truth for the
> coding agent. When a prompt and this file disagree, this file wins unless the prompt says
> "override".

> **Repository note (keep in README):** this repo is a sandbox for evaluating AI code-review
> tools. Some pull requests intentionally contain defects. Do not use this code in production.

---

## 1. Purpose and scope

A payments-style backend that:

1. **Onboards merchants** one at a time (REST) and in bulk (CSV upload, processed asynchronously).
2. **Captures transactions** for merchants.
3. **Runs daily settlement**: groups captured transactions per merchant, applies MDR (merchant
   discount rate), and produces settlement records. Runs asynchronously, is idempotent, and is
   safe under concurrent requests.
4. **Generates reports** (settlement report as JSON or streamed CSV).
5. Secures everything with JWT auth and role-based access (ADMIN, OPS, MERCHANT).

Out of scope: real bank integrations, real payment rails, UI, multi-tenancy, Kafka. All money is
fictional.

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3.x (Web, Validation, Data JPA, Security, Actuator) |
| Build | Maven with wrapper (`./mvnw`) |
| Database | PostgreSQL 16 |
| Migrations | Flyway (`src/main/resources/db/migration`) |
| Auth | Spring Security, stateless JWT (HS256 via `jjwt` or Nimbus), BCrypt password hashing |
| API docs | springdoc-openapi (`/swagger-ui.html`) |
| Mapping | Hand-written mappers (no MapStruct, no Lombok, keep diffs readable; use Java records for DTOs) |
| Testing | JUnit 5, Mockito, AssertJ, Spring Boot Test, Testcontainers (PostgreSQL), MockMvc |
| Quality | JaCoCo coverage report, Spotless (google-java-format) or Checkstyle |
| Local dev | `docker-compose.yml` with Postgres, `Dockerfile` (multi-stage) |
| CI | GitHub Actions: build, test, coverage artifact |

Base package: `io.settlementhub`.

## 3. Architecture

Package-by-feature, layered inside each feature. Controllers never touch repositories directly.

```
io.settlementhub
├── SettlementHubApplication.java
├── common
│   ├── config          (AsyncConfig, JacksonConfig, OpenApiConfig, AppProperties)
│   ├── error           (ApiException, ErrorCode, GlobalExceptionHandler -> RFC 7807 ProblemDetail)
│   ├── logging         (CorrelationIdFilter, MDC helpers)
│   ├── money           (Money utilities, MdrCalculator)
│   ├── audit           (AuditLog entity, AuditService)
│   └── web             (PageResponse, IdempotencyKeyResolver)
├── security
│   ├── JwtService, JwtAuthFilter, SecurityConfig, CurrentUser
│   └── user            (UserAccount entity, repository, AuthController, AuthService)
├── merchant            (entity, repo, service, controller, dto, mapper, MerchantStatus)
├── transaction         (entity, repo, service, controller, dto)
├── settlement          (batch + settlement entities, repos, SettlementService,
│                        SettlementWorker, SettlementController, dto)
├── onboarding          (job + row-error entities, CsvOnboardingProcessor,
│                        OnboardingService, OnboardingController, dto)
└── report              (ReportService, ReportController, CsvWriter)
```

Rules:
- DTOs are records. Entities never leave the service layer.
- `@Transactional` lives on service methods, never on controllers.
- All time values are `Instant` (UTC) in the database and API. Business dates are `LocalDate`.
- Money is stored as `long` minor units (paise/cents) plus an ISO-4217 `currency` char(3).
  `BigDecimal` is used only inside `MdrCalculator`.
- Constructor injection only.

## 4. Data model (Flyway migrations, one file per concern)

### `users`
`id uuid pk, username varchar(64) unique not null, password_hash varchar(100) not null,
role varchar(16) not null  -- ADMIN | OPS | MERCHANT, merchant_id uuid null fk, created_at timestamptz`

### `merchants`
`id uuid pk, legal_name varchar(200) not null, business_email varchar(200) unique not null,
phone varchar(20), status varchar(16) not null -- PENDING | ACTIVE | SUSPENDED,
mdr_bps int not null check (mdr_bps between 0 and 2000), bank_account_ref varchar(64),
created_at, updated_at timestamptz, version bigint not null default 0`

### `transactions`
`id uuid pk, merchant_id uuid fk not null, external_ref varchar(64) not null,
amount_minor bigint not null check (amount_minor > 0), currency char(3) not null,
status varchar(16) not null -- CAPTURED | SETTLED | REFUNDED, captured_at timestamptz not null,
settlement_id uuid null, unique (merchant_id, external_ref)`
Indexes: `(merchant_id, status, captured_at)`, `(settlement_id)`.

### `settlement_batches`
`id uuid pk, business_date date not null, status varchar(20) not null
-- CREATED | RUNNING | COMPLETED | PARTIAL_FAILED | FAILED,
total_merchants int not null default 0, processed_merchants int not null default 0,
failed_merchants int not null default 0, idempotency_key varchar(80) unique not null,
started_at, completed_at, created_at timestamptz, version bigint not null default 0`

### `settlements`
`id uuid pk, batch_id uuid fk not null, merchant_id uuid fk not null, gross_minor bigint,
mdr_minor bigint, net_minor bigint, txn_count int, currency char(3), status varchar(16)
-- CREATED | FAILED, failure_reason varchar(500), created_at timestamptz,
unique (batch_id, merchant_id)`

### `onboarding_jobs`
`id uuid pk, file_name varchar(200), status varchar(16) -- QUEUED | RUNNING | COMPLETED | FAILED,
total_rows int, success_rows int, failed_rows int, created_by varchar(64), created_at, completed_at`

### `onboarding_row_errors`
`id bigserial pk, job_id uuid fk, row_no int, message varchar(500)`

### `audit_log`
`id bigserial pk, actor varchar(64), action varchar(64), entity_type varchar(64),
entity_id varchar(64), at timestamptz, details jsonb`

Seed migration: one ADMIN user (`admin`, password injected from env var `APP_ADMIN_PASSWORD`
at startup by a `CommandLineRunner`, never hard-coded in SQL), plus 5 demo merchants under
the `dev` profile only.

## 5. API design (all under `/api/v1`, JSON, errors are RFC 7807)

| Method & path | Role | Description |
|---|---|---|
| `POST /auth/login` | public | `{username,password}` -> `{accessToken, expiresInSeconds}` |
| `POST /merchants` | ADMIN, OPS | Create merchant (status PENDING) |
| `GET /merchants/{id}` | ADMIN, OPS, owning MERCHANT | Fetch merchant |
| `GET /merchants?status=&q=&page=&size=` | ADMIN, OPS | Paginated search (max size 100) |
| `PATCH /merchants/{id}/status` | ADMIN | Activate / suspend; audit logged |
| `POST /transactions` | ADMIN, OPS | Capture a transaction (idempotent on merchant + externalRef) |
| `GET /transactions?merchantId=&from=&to=&page=&size=` | ADMIN, OPS, owning MERCHANT | Paginated list |
| `POST /settlements/batches` | ADMIN, OPS | Body `{businessDate}`, header `Idempotency-Key`; returns `202` + batch id |
| `GET /settlements/batches/{id}` | ADMIN, OPS | Batch progress |
| `GET /settlements/batches/{id}/settlements` | ADMIN, OPS | Paginated settlements |
| `POST /onboarding/jobs` | ADMIN | Multipart CSV upload; returns `202` + job id |
| `GET /onboarding/jobs/{id}` | ADMIN | Job progress |
| `GET /onboarding/jobs/{id}/errors` | ADMIN | Row errors |
| `GET /reports/settlements?from=&to=&format=json\|csv` | ADMIN, OPS | Settlement report; CSV is streamed (PR 3 ships it as `/reports/daily-merchant-summary`, built over transactions) |
| `GET /actuator/health` | public | Liveness/readiness |

Conventions: pagination uses `page` (0-based) and `size`; list responses are
`PageResponse<T>{items, page, size, totalElements, totalPages}`. Validation failures return 400
with a `violations` array. Unknown ids return 404. Duplicate natural keys return 409.

## 6. Key flows

### 6.1 Settlement (asynchronous, idempotent)
1. Controller validates the date (not in the future) and reads `Idempotency-Key`.
2. `SettlementService.createBatch` inserts a batch row. If the key already exists, return the
   existing batch (same `202` response) instead of creating a second one.
3. After the transaction commits, `SettlementWorker.run(batchId)` is submitted to a bounded
   `ThreadPoolTaskExecutor` (`settlementExecutor`: core 4, max 8, queue 100, `CallerRunsPolicy`
   is NOT allowed; use abort policy and map rejection to 429).
4. Worker finds merchants that have `CAPTURED` transactions on the business date, then settles
   **one merchant per transaction** (`REQUIRES_NEW`): lock the merchant's captured transactions,
   compute gross, MDR = `round(gross * mdr_bps / 10000, HALF_UP)`, net = gross - MDR, insert the
   settlement row, mark transactions `SETTLED` with `settlement_id`.
5. A failure for one merchant is recorded on a FAILED settlement row; other merchants continue.
6. Batch progress counters update after each merchant; final status is COMPLETED,
   PARTIAL_FAILED or FAILED.
7. Re-running the same business date must never double-settle a transaction.

### 6.2 Bulk onboarding
CSV columns: `legal_name,business_email,phone,mdr_bps`. Upload stores a job row and hands the
bytes to `CsvOnboardingProcessor`, which streams the file line by line (never loads the whole
file), validates each row, inserts valid merchants in chunks of 500, records row-level errors,
and updates job counters. A bad row never fails the whole job.

### 6.3 Reports
Settlement report over a date range. JSON is paginated. CSV is streamed directly to the response
(`StreamingResponseBody`) from a repository stream, using constant memory, with
`Content-Disposition: attachment`.

## 7. Cross-cutting concerns

- **Errors:** `GlobalExceptionHandler` maps `ApiException(ErrorCode, message)`,
  `MethodArgumentNotValidException`, `ConstraintViolationException`,
  `OptimisticLockingFailureException` (409), `TaskRejectedException` (429), and a safe 500 that
  never leaks stack traces.
- **Security:** stateless; `JwtAuthFilter` validates the token; method security via
  `@PreAuthorize`; merchants can only access their own `merchant_id`. JWT secret comes from
  `APP_JWT_SECRET` (min 32 bytes), startup fails if missing in non-`dev` profiles.
- **Logging:** `CorrelationIdFilter` sets `X-Correlation-Id` (generate if absent) into MDC.
  Never log phone numbers, emails or tokens; mask where needed.
- **Audit:** status changes, onboarding uploads and settlement runs write `audit_log` rows.
- **Config:** `application.yml` plus `application-dev.yml`, `application-test.yml`. All tunables
  (pool sizes, chunk size, token TTL) live in a typed `@ConfigurationProperties` class.
- **Observability:** Actuator health, plus Micrometer counters for settlements processed and
  failed.

## 8. Concurrency design notes

- Optimistic locking (`@Version`) on merchants and batches.
- Per-merchant settlement uses `SELECT ... FOR UPDATE` (or `@Lock(PESSIMISTIC_WRITE)`) on the
  merchant's captured transactions so two workers cannot settle the same rows.
- Batch counters are updated with atomic SQL (`update ... set processed = processed + 1`), not
  read-modify-write in Java.
- Idempotency is enforced by a unique constraint, not just an `if` check.
- The executor is bounded and sized from config.

## 9. Testing strategy

- **Unit:** `MdrCalculator` (rounding edge cases, zero and max bps), services with Mockito.
- **Web slice:** `@WebMvcTest` for validation, error mapping, and role access.
- **Integration:** Testcontainers PostgreSQL; full flows for onboarding, settlement idempotency,
  concurrent settlement of the same date (two threads, assert no double-settlement), CSV report.
- Target: 80%+ line coverage on `settlement`, `onboarding`, `merchant`, `common.money`.
- Tests are part of every PR, not a follow-up.

## 10. CI (GitHub Actions)

`.github/workflows/ci.yml`: on push and pull_request, Java 21 (Temurin), Maven cache, run
`./mvnw -B verify`, upload JaCoCo report. Testcontainers works on the default runner.

## 11. Delivery plan

| Step | Branch | Content |
|---|---|---|
| Baseline | `main` | Project skeleton, schema, security, merchant + transaction CRUD, CI, docs |
| PR 1 | `feat/async-settlement-engine` | Section 6.1: batch settlement with async workers |
| PR 2 | `feat/merchant-search-and-merchant-portal-access` | Merchant search, merchant-scoped access, audit log |
| PR 3 | `refactor/streaming-reports-and-csv-processing` | Sections 6.2 and 6.3: shared file-processing template, streamed reports |

Each PR must be reviewable: 300 to 900 changed lines including tests, one clear purpose, a
descriptive title, and a PR body with Summary, Changes, How to test, and Risks.

## 12. Definition of done

- `./mvnw -B verify` passes locally and in CI.
- OpenAPI renders and matches the real endpoints.
- README explains setup (`docker compose up -d`, env vars, running tests, sample curl calls).
- No secrets committed. `.env.example` lists required variables.