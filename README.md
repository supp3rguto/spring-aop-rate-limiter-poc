# spring-aop-rate-limiter-poc

Application-level **API Rate Limiter** built with Java 21, Spring Boot, Spring AOP, and MySQL.

Instead of delegating traffic control to an external API gateway, this project implements rate limiting directly inside the service layer to practice cross-cutting request interception, database-level concurrency control, continuous Token Bucket math, and standard HTTP rate-limit contracts.

## Core engineering concepts

Each layer of the backend addresses a specific concurrency, persistence, or HTTP contract problem:

- **Declarative interception (`Spring AOP` + `@Around`):** endpoints opt into rate limiting through a custom `@RateLimited(endpointName = ...)` annotation. `RateLimiterAspect` intercepts calls before controller execution, attaches quota headers to allowed responses, and aborts execution when tokens are exhausted without mixing throttling logic into business methods.
- **Database-level concurrency control (`Pessimistic Write Lock`):** `ApiKeyRepository.findByIdForUpdate` acquires a row-level lock (`SELECT ... FOR UPDATE`) on `api_keys` inside a transaction to prevent lost updates under concurrent bursts from the same client.
- **Continuous Token Bucket algorithm:** tokens refill proportionally to elapsed time (`elapsedMs * capacity / refillPeriodMs`), preserving fractional elapsed time across requests so bursts are smoothed out rather than resetting in fixed blocks. Rejected requests (`429`) do not write to the database.
- **Deterministic time abstraction (`java.time.Clock`):** system time is injected as a Spring bean rather than calling `System.currentTimeMillis()` directly, enabling deterministic refill and retry-window calculations.
- **Weighted endpoints and tiered plans:** rate limits are driven by relational data (`plans`, `api_keys`, `endpoint_costs`), allowing different endpoints to consume different token weights per request (e.g., `1` token for reads, `5` tokens for resource creation).
- **Standard HTTP contract and error handling:** exposes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, and `Retry-After` headers (also whitelisted via CORS `exposedHeaders`), validates payloads with Bean Validation (`@Valid`), and returns structured RFC 9457 `ProblemDetail` (`application/problem+json`) responses for `400`, `401`, and `429`.
- **Reproducible schema and local setup:** MySQL 8.4 runs via Docker Compose with healthchecks, schema evolution is managed by Flyway (`V1`, `V2`), and Hibernate runs with `ddl-auto=validate` and `open-in-view=false`.

## Stack and tools

- Java `21` and Spring Boot `3.5.7` (via Maven Wrapper `./mvnw`)
- Spring Web, Spring AOP, Spring Data JPA, Spring Validation
- MySQL `8.4` (Docker Compose) and Flyway migrations
- Lombok (getter/setter boilerplate only; explicit `equals`/`hashCode` on JPA entities)
- React / Vite (existing demo client under `frontend/`)

## Project structure

```text
spring-aop-rate-limiter-poc/
├── docker-compose.yml                                    # Local MySQL 8.4 container with healthcheck and persistent volume
├── README.md
├── backend/
│   ├── mvnw                                              # Maven Wrapper (no global Maven install required)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/backend/ratelimiter/
│       │   │   ├── RateLimiterApplication.java           # Spring Boot entrypoint
│       │   │   ├── aop/
│       │   │   │   ├── RateLimited.java                  # Custom method-level annotation with endpointName
│       │   │   │   ├── RateLimitHeaders.java             # Standard X-RateLimit-* and Retry-After header helpers
│       │   │   │   └── RateLimiterAspect.java            # @Around aspect validating X-API-KEY and enforcing quotas
│       │   │   ├── config/
│       │   │   │   ├── ClockConfig.java                  # Injectable UTC Clock bean
│       │   │   │   └── WebConfig.java                    # Configurable CORS origins and exposed rate-limit headers
│       │   │   ├── controller/
│       │   │   │   ├── ApiController.java                # Public, limited, and weighted POST /api/resources endpoints
│       │   │   │   └── dto/
│       │   │   │       ├── ApiMessageResponse.java       # Standard JSON message payload
│       │   │   │       ├── CreateResourceRequest.java    # Validated request body (@NotBlank, @Size)
│       │   │   │       └── ResourceResponse.java         # Created resource response payload
│       │   │   ├── exception/
│       │   │   │   ├── ApiKeyInvalidException.java       # Missing or unknown API key (401)
│       │   │   │   ├── RateLimitException.java           # Quota exhausted carrying RateLimitResult metadata (429)
│       │   │   │   └── GlobalExceptionHandler.java       # RFC 9457 ProblemDetail mapping for 400, 401, and 429
│       │   │   ├── model/
│       │   │   │   ├── ApiKey.java                       # API key state (tokens, lastRefillTimestamp, LAZY Plan)
│       │   │   │   ├── EndpointCost.java                 # Per-endpoint token weight
│       │   │   │   └── Plan.java                         # Bucket capacity and refill period
│       │   │   ├── repository/
│       │   │   │   ├── ApiKeyRepository.java             # Pessimistic write lock query (findByIdForUpdate)
│       │   │   │   ├── EndpointCostRepository.java       # Lookup by endpointName
│       │   │   │   └── PlanRepository.java
│       │   │   └── service/
│       │   │       ├── RateLimitResult.java              # Immutable decision record (allowed, limit, remaining, reset, retryAfter)
│       │   │       └── RateLimitingService.java          # Transactional Token Bucket engine
│       │   └── resources/
│       │       ├── application.properties                # Env-var driven datasource, Flyway, JPA, and CORS settings
│       │       └── db/migration/
│       │           ├── V1__create_schema.sql             # Schema for plans, api_keys, and endpoint_costs
│       │           └── V2__seed_initial_data.sql         # Dev plans (Free, Pro), API keys, and endpoint costs
│       └── test/
│           └── java/com/backend/ratelimiter/
│               └── RateLimiterApplicationTests.java      # Application context smoke test
└── frontend/                                             # React/Vite client (pending alignment with new JSON/POST contract)

```

## Architectural notes and trade-offs

**MySQL `SELECT ... FOR UPDATE` vs. Redis + Lua:**
Persisting token state in MySQL with row-level pessimistic locking guarantees strict consistency across concurrent requests without adding a second stateful infrastructure component. The trade-off is higher per-request latency and lower peak throughput compared to an in-memory store like Redis running atomic Lua scripts. For a high-throughput distributed edge gateway, Redis (or an edge proxy) is the standard choice; for a self-contained service with low-to-moderate RPS and relational billing plans, transactional row locking keeps consistency simple and auditable.

**Avoiding lock contention on `plans`:**
`ApiKey.plan` uses `FetchType.LAZY` and `findByIdForUpdate` deliberately omits `JOIN FETCH k.plan`. In MySQL InnoDB, combining `JOIN` with `FOR UPDATE` locks the referenced row in `plans` as well, which would serialize unrelated clients sharing the same tier (e.g., all `Free` users). Locking only `api_keys` serializes concurrent requests for the same key while keeping different clients parallel.

**Spring AOP vs. Servlet Filter / API Gateway:**
Using an `@Around` aspect keeps rate-limit configuration close to controller methods via `@RateLimited`. Because Spring MVC resolves and validates `@Valid @RequestBody` arguments before invoking the controller proxy, malformed requests return `400 Bad Request` without consuming user tokens. If the requirement were to throttle abusive invalid payloads before JSON deserialization, enforcement would need to move up to a `OncePerRequestFilter` or an API gateway.

## Running locally

### Prerequisites

* Java `21` (`java -version`)
* Docker Engine and Docker Compose (`docker compose version`)
* `curl` for manual HTTP inspection

### Step by step

1. Clone the repository and start MySQL:

```bash
git clone [https://github.com/supp3rguto/spring-aop-rate-limiter-poc.git](https://github.com/supp3rguto/spring-aop-rate-limiter-poc.git)
cd spring-aop-rate-limiter-poc
docker compose up -d
docker compose ps

```

Wait until `ratelimiter-mysql` reports `healthy`.

2. Start the Spring Boot backend (Flyway applies `V1` and `V2` automatically on startup):

```bash
cd backend
./mvnw spring-boot:run

```

Configuration can be overridden via environment variables: `SERVER_PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `CORS_ALLOWED_ORIGINS`.

3. Run the current Maven test suite:

```bash
cd backend
./mvnw clean test

```

### Seeded development API keys

| API Key | Plan | Bucket Capacity | Refill Window |
| --- | --- | --- | --- |
| `augusto-dev-key` | `Free` | `10` tokens | `60` seconds (`1` token every `6s`) |
| `pro-dev-key` | `Pro` | `1000` tokens | `60` seconds |

### Manual concurrency check

Reset the `Free` key to full capacity and fire 15 concurrent requests against `/api/limited` (cost `1`):

```bash
docker exec -i ratelimiter-mysql mysql -u ratelimiter -pratelimiter_dev ratelimit_db \
  -e "UPDATE api_keys SET tokens = 10, last_refill_timestamp = 0 WHERE api_key = 'augusto-dev-key';"

seq 1 15 | xargs -P 15 -I {} curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-API-KEY: augusto-dev-key" http://localhost:8080/api/limited | sort | uniq -c

```

Expected output:

```text
     10 200
      5 429

```

## Endpoints and HTTP contract

| Method | Path | Header Required | Endpoint Name | Token Cost | Success Status |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/public` | None | — | `0` | `200 OK` |
| `GET` | `/api/limited` | `X-API-KEY` | `get_limited_data` | `1` | `200 OK` |
| `POST` | `/api/resources` | `X-API-KEY` | `create_resource` | `5` | `201 Created` |

### Rate-limit response headers

* `X-RateLimit-Limit`: total bucket capacity for the key's plan.
* `X-RateLimit-Remaining`: remaining tokens after evaluating the request.
* `X-RateLimit-Reset`: estimated Unix epoch timestamp (in seconds) when the bucket reaches full capacity.
* `Retry-After`: minimum wait time in seconds before enough tokens are available (returned on `429`).

### Examples with `curl`

```bash
# 200 OK — public endpoint (no rate-limit headers)
curl -i http://localhost:8080/api/public

# 401 Unauthorized — missing X-API-KEY (application/problem+json)
curl -i http://localhost:8080/api/limited

# 400 Bad Request — invalid payload (does not consume tokens)
curl -i -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: augusto-dev-key" \
  -d '{"name": "   "}'

# 201 Created — consumes 5 tokens (returns X-RateLimit-Limit, Remaining, Reset)
curl -i -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: augusto-dev-key" \
  -d '{"name": "report-q4"}'

# 429 Too Many Requests — quota exhausted (returns Retry-After + ProblemDetail)
curl -i -H "X-API-KEY: augusto-dev-key" http://localhost:8080/api/limited

```

## Progress and next steps

* [x] Docker Compose MySQL 8.4 setup and env-var driven configuration
* [x] Versioned Flyway migrations (`V1`, `V2`) with `ddl-auto=validate` and `open-in-view=false`
* [x] JPA entity cleanup (`FetchType.LAZY`, proxy-safe `equals`/`hashCode`) and constructor injection
* [x] Pessimistic locking (`SELECT ... FOR UPDATE`) on `api_keys` to eliminate lost updates
* [x] Continuous Token Bucket refill with fractional time preservation, injectable `Clock`, and zero writes on `429`
* [x] `@Around` AOP interception exposing `X-RateLimit-*` and `Retry-After` headers
* [x] RFC 9457 `ProblemDetail` error handling (`400`, `401`, `429`), CORS `exposedHeaders`, and `POST /api/resources`
* [ ] Unit tests for `RateLimitingService` using a fixed `Clock` (refill math, fractional remainder, retry calculation, unknown key)
* [ ] Integration and concurrency test suite using Testcontainers (real MySQL 8.4) verifying zero lost updates under parallel load
* [ ] GitHub Actions CI workflow running `./mvnw verify` on pull requests and pushes
* [ ] Caching for `endpoint_costs` lookups to reduce per-request database reads
* [ ] Update React/Vite dashboard (`frontend/`) to consume the JSON responses, `POST /api/resources`, and rate-limit headers

## Author

Augusto Ortigoso Barbosa

* GitHub: https://github.com/supp3rguto
* LinkedIn: https://www.linkedin.com/in/augusto-barbosa-769602194
