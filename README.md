# Settlement Hub

Settlement Hub is a payments-style Spring Boot backend for merchant onboarding, transaction
capture and daily settlement. It exposes a versioned REST API secured with stateless JWT
authentication and role-based access control.

> **Review-tool sandbox:** this repository is a sandbox for evaluating AI code-review tools.
> Some pull requests intentionally contain defects. Do not use this code in production.

## Features

- Merchant onboarding and lifecycle management (`PENDING`, `ACTIVE`, `SUSPENDED`)
- Transaction capture with idempotency on `(merchantId, externalRef)`
- Stateless JWT authentication with `ADMIN`, `OPS` and `MERCHANT` roles
- PostgreSQL schema managed by Flyway
- RFC 7807 problem responses, correlation ids and structured validation errors
- OpenAPI documentation and Swagger UI
- Actuator health probes, JaCoCo coverage and GitHub Actions CI

The asynchronous settlement engine, bulk CSV onboarding, streamed reports and audit log are
delivered in follow-up pull requests.

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x (Web, Validation, Data JPA, Security, Actuator) |
| Build | Maven wrapper (`./mvnw`) |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (HS256, jjwt), BCrypt |
| API docs | springdoc-openapi |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers |
| Quality | JaCoCo, Spotless (google-java-format) |

## Project structure

```
src/main/java/io/settlementhub
├── common
│   ├── config       AppProperties, JacksonConfig, OpenApiConfig
│   ├── error        ApiException, ErrorCode, GlobalExceptionHandler
│   ├── logging      CorrelationIdFilter
│   ├── money        MdrCalculator
│   └── web          PageResponse, Pagination
├── security
│   ├── CurrentUser, CurrentUserProvider, JwtService, JwtAuthFilter, SecurityConfig
│   └── user         UserAccount, AuthService, AuthController, admin bootstrap
├── merchant         Merchant entity, repository, service, controller, DTOs, mapper
├── transaction      Transaction entity, repository, service, controller, DTOs, mapper
└── dev              Demo data seeder (dev profile only)
```

The code is organised package-by-feature. Controllers never access repositories directly,
DTOs are records, entities never leave the service layer, and transaction boundaries live on
service methods. Money is stored as minor units (`long`) and MDR rounds with `HALF_UP` inside
`MdrCalculator`.

## Prerequisites

- Java 21
- Docker (for PostgreSQL and Testcontainers)
- No local Maven installation is required; use `./mvnw`

## Configuration

All tunables are bound to `AppProperties` (`app.*`).

| Variable | Required | Description |
|---|---|---|
| `APP_JWT_SECRET` | yes (non-dev) | HMAC secret, minimum 32 bytes |
| `APP_ADMIN_PASSWORD` | yes | Password used once to create the bootstrap `admin` user |
| `APP_ADMIN_USERNAME` | no | Bootstrap admin username, defaults to `admin` |
| `APP_JWT_TTL_SECONDS` | no | Access token lifetime, defaults to 3600 |
| `SPRING_DATASOURCE_URL` | no | JDBC URL, defaults to `jdbc:postgresql://localhost:5433/settlement_hub` |
| `SPRING_DATASOURCE_USERNAME` | no | Database user, defaults to `settlement` |
| `SPRING_DATASOURCE_PASSWORD` | no | Database password, defaults to `settlement` |
| `SERVER_PORT` | no | HTTP port, defaults to `8083` |

The admin account is created by a `CommandLineRunner` from `APP_ADMIN_PASSWORD`; the password
never appears in SQL or source. The application fails fast when required secrets are missing.

## Run with Docker

```bash
cp .env.example .env
# Replace the placeholders with generated values, for example:
#   openssl rand -hex 32   -> APP_JWT_SECRET
#   openssl rand -hex 16   -> APP_ADMIN_PASSWORD, POSTGRES_PASSWORD
docker compose up --build
```

The API listens on <http://localhost:8083>. PostgreSQL is published on host port `5433`.

```bash
curl http://localhost:8083/actuator/health
open http://localhost:8083/swagger-ui.html
```

To run only PostgreSQL in Docker and the application from your IDE:

```bash
docker compose up -d postgres
set -a; source .env; set +a
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile seeds five demo merchants when the database is empty.

## Tests

```bash
./mvnw -B verify
```

`verify` runs unit tests, `@WebMvcTest` slices, Testcontainers integration tests, JaCoCo and the
Spotless check. Docker must be running for the Testcontainers-backed tests. The coverage report
is written to `target/site/jacoco/index.html`.

## API examples

Log in as the bootstrap admin:

```bash
set -a; source .env; set +a
TOKEN=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$APP_ADMIN_PASSWORD\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
```

Create a merchant:

```bash
curl -s -X POST http://localhost:8083/api/v1/merchants \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "legalName": "Example Merchant",
        "businessEmail": "payments@example.com",
        "phone": "+1-555-0199",
        "mdrBps": 150,
        "bankAccountRef": "BANK-EXAMPLE-01"
      }'
```

Capture a transaction:

```bash
curl -s -X POST http://localhost:8083/api/v1/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "merchantId": "<merchant-id>",
        "externalRef": "order-1001",
        "amountMinor": 12500,
        "currency": "USD"
      }'
```

List transactions:

```bash
curl -s "http://localhost:8083/api/v1/transactions?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

Every response carries `X-Correlation-Id`; send your own value to propagate it through logs and
problem responses.
