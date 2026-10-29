# Vehicle Title Transaction API

A production-minded Java 21 service for reliable vehicle-to-government title
workflows. It exposes the same transaction capabilities through modern REST and
contract-first SOAP interfaces while protecting record integrity across retries,
concurrent callbacks, and downstream failures.

## Why this project

Vehicle title integrations combine long-running state workflows, legacy SOAP
contracts, newer REST clients, jurisdiction-specific data, and strict audit needs.


## Highlights

- Java 21 virtual threads and Spring Boot 3
- Contract-first SOAP with a versioned XSD, generated JAXB models, WSDL, and SOAP faults
- REST API with OpenAPI/Swagger documentation and RFC 9457 Problem Details
- PostgreSQL persistence with Hibernate/JPA and Flyway migrations
- Payload-aware idempotency keys backed by a database uniqueness constraint
- Controlled transaction state machine with locked status updates
- Immutable audit history across REST and SOAP channels
- Transactional outbox with bounded exponential-backoff retries
- Resilience4j circuit breaker on outbound webhook delivery, layered on top of outbox retries
- Spring Cloud Gateway edge service with routing, read retries, a circuit breaker, and an RFC 9457 fallback
- Distributed tracing with OpenTelemetry across gateway, API, database calls, and outbound webhooks, viewed in Jaeger
- API-key authentication, request validation, correlation IDs, health probes, and metrics
- Docker Compose, Kubernetes manifests, GitHub Actions, JaCoCo, and ArchUnit

## Architecture

```mermaid
flowchart TB
    REST[REST clients] --> GW[Spring Cloud Gateway<br/>routing, retries, circuit breaker]
    SOAP[SOAP partners] --> GW
    GW --> API[REST adapter]
    GW --> WS[SOAP endpoint]
    API --> SVC[Transaction service]
    WS --> SVC
    SVC --> DB[(PostgreSQL)]
    DB --> OUTBOX[Outbox dispatcher]
    OUTBOX --> CB{Resilience4j<br/>circuit breaker}
    CB --> GOV[State title registry webhook]
    GW -. OTLP traces .-> JAEGER[Jaeger]
    API -. OTLP traces .-> JAEGER
```

The REST and SOAP layers contain protocol mapping only. Business rules live in a
shared application service and domain model. See
[architecture decisions](docs/architecture-decisions.md) for the tradeoffs.

## Transaction lifecycle

```mermaid
stateDiagram-v2
    [*] --> RECEIVED
    RECEIVED --> VALIDATING
    RECEIVED --> REJECTED
    VALIDATING --> SUBMITTED_TO_STATE
    VALIDATING --> REJECTED
    SUBMITTED_TO_STATE --> COMPLETED
    SUBMITTED_TO_STATE --> REJECTED
```

## Run locally

Requirements: Docker with Compose.

```bash
docker compose up --build
```

The local API key is `local-development-key`. Partners call the gateway on port 8000;
the API is also exposed directly on 8080 for debugging.

- Gateway: <http://localhost:8000/api/v1/title-transactions>
- Traces (Jaeger): <http://localhost:16686>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- SOAP WSDL: <http://localhost:8080/ws/title-transactions.wsdl>
- Health: <http://localhost:8080/actuator/health>
- Prometheus metrics: <http://localhost:8080/actuator/prometheus>

REST requests are available in [`examples/rest-api.http`](examples/rest-api.http).

## REST API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/title-transactions` | Submit or safely replay a transaction |
| `GET` | `/api/v1/title-transactions/{id}` | Read a transaction |
| `GET` | `/api/v1/title-transactions?vin={vin}` | Find the history for a VIN |
| `PATCH` | `/api/v1/title-transactions/{id}/status` | Advance the controlled lifecycle |
| `GET` | `/api/v1/title-transactions/{id}/audit-events` | Read the audit history |

Every protected REST request requires `X-API-Key`. Submission also requires an
`Idempotency-Key` header.

## SOAP API

The contract lives at
[`src/main/resources/xsd/title-transactions-v1.xsd`](src/main/resources/xsd/title-transactions-v1.xsd).
Maven generates the binding classes during `generate-sources`; Spring-WS publishes
the WSDL at runtime.

```bash
curl --fail-with-body http://localhost:8080/ws \
  -H 'Content-Type: text/xml; charset=utf-8' \
  -H 'X-API-Key: local-development-key' \
  --data-binary @examples/soap-submit.xml
```

Available operations:

- `SubmitTitleTransaction`
- `GetTitleTransaction`

SOAP idempotency is part of the request contract. Invalid requests, conflicting
idempotency keys, and missing transactions are returned as client SOAP faults.

## Reliability behavior

When a submission commits, three records are written atomically:

1. The current title transaction.
2. An immutable audit event.
3. A pending integration event in the outbox.

The dispatcher provides at-least-once delivery and exponential backoff. Two publishers
sit behind the `OutboxPublisher` interface: a logging adapter for local work, and an
HTTP adapter (`OUTBOX_PUBLISHER=http`) that posts each event to the title registry
webhook with the outbox event ID as its `Idempotency-Key`.

### Circuit breaker

The HTTP publisher is wrapped in a Resilience4j circuit breaker (`titleRegistry`):

- It opens when at least half of the last 10 calls fail or take longer than 1.5 seconds.
- While open, the dispatcher stops the batch and leaves events pending **without**
  counting an attempt, so an outage does not burn through an event's retry budget.
- After 30 seconds it lets 3 trial calls through; if they succeed, it closes again.
- A 4xx response is ignored by the breaker, because it means the event was rejected,
  not that the registry is down.

Its state is visible at `/actuator/health` and as `resilience4j_circuitbreaker_*`
metrics in Prometheus. To see it work locally, run `docker compose stop title-registry`,
submit a few transactions, and watch the health status change to `CIRCUIT_OPEN`.

### Gateway

The `gateway/` service is a Spring Cloud Gateway in front of the API:

- Routes `/api/**` and `/ws/**` to the API and adds an `X-Gateway` header.
- Retries `GET` requests twice on 502 or 503. Writes are not retried at the edge; a
  client replays a submission safely with its own `Idempotency-Key`.
- Wraps every route in a circuit breaker with a 5-second time limit. When the API is
  down, clients get a 503 Problem Details response with `Retry-After: 30`.

### Tracing

The gateway and the API export OpenTelemetry traces over OTLP through Micrometer
Tracing. A single trace shows the gateway hop, the API request, its database calls,
and the outbound webhook call, with W3C `traceparent` headers carrying the context
between services. Trace and span IDs are added to log lines, so a log entry can be
matched to its trace in Jaeger.

## Tests

```bash
mvn clean verify
```

The suite covers domain transitions, idempotent replay and conflicts, audit creation,
API authentication, contract-first SOAP requests and faults, outbox backoff, circuit
breaker behavior, and architectural dependency rules. The gateway has its own suite
(`cd gateway && mvn verify`) covering routing, read retries, and the fallback. GitHub Actions runs the suite with Java 21 and uploads
Surefire and JaCoCo reports.

## Configuration

| Variable | Purpose | Local default |
|---|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/vehicle_titles` |
| `DATABASE_USERNAME` | Database user | `vehicle_titles` |
| `DATABASE_PASSWORD` | Database password | `vehicle_titles` |
| `APP_API_KEY` | Partner API credential | `dev-secret-change-me` |
| `OUTBOX_PUBLISHER` | `logging` or `http` | `logging` |
| `OUTBOX_WEBHOOK_URL` | Title registry webhook for the HTTP publisher | `http://localhost:8081/title-events` |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | Where traces are sent | `http://localhost:4318/v1/traces` |
| `TRACING_SAMPLING_PROBABILITY` | Share of requests traced | `1.0` |

Never use the local defaults in a deployed environment. Kubernetes values are split
between a ConfigMap and a Secret example under [`k8s/`](k8s/).

## Technology

Java 21 with virtual threads, Spring Boot 3, Spring Web Services, Spring Security, Hibernate/JPA,
PostgreSQL, Flyway, Maven, JUnit 5, MockMvc, Spring-WS Test, ArchUnit, Docker,
Kubernetes, GitHub Actions, OpenAPI, Actuator, Prometheus, Resilience4j, Spring Cloud
Gateway, OpenTelemetry (Micrometer Tracing, OTLP), and Jaeger.
