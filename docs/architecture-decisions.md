# Architecture decisions

## Contract-first SOAP

The SOAP contract begins with a versioned XSD. Maven generates Jakarta XML Binding
classes from that schema, and Spring Web Services derives the WSDL. This keeps the
wire contract explicit and prevents Java implementation details from shaping the
integration contract accidentally.

The REST controller and SOAP endpoint are delivery adapters over the same
`TitleTransactionService`. They therefore share validation, idempotency, lifecycle,
auditing, persistence, and outbox behavior.

## Idempotent submission

Every caller supplies an idempotency key. The service hashes a canonical form of the
business request and stores both values with the transaction. A replay with the same
key and payload returns the original resource. Reusing a key with a different payload
returns a conflict. A database unique constraint is the final concurrency guard.

## Controlled state transitions

The aggregate owns its transition rules. Delivery code cannot skip directly from
`RECEIVED` to `COMPLETED`, and terminal states cannot be reopened. Status updates use
a pessimistic row lock so concurrent state callbacks cannot silently overwrite one
another.

## Transactional outbox

The transaction, audit event, and integration event are written in one database
transaction. A scheduled dispatcher publishes due events and records exponential
backoff after failures. Delivery is at least once; an integration consumer should use
the outbox event ID as its idempotency key.

`LoggingOutboxPublisher` is a local adapter. It can be replaced with Kafka, Pub/Sub,
or an agency-specific client without changing the transaction use cases.

## Security and observability

- Integration endpoints require an API key supplied through configuration.
- Key comparison is constant time; application sessions and CSRF are disabled for the
  stateless machine-to-machine API.
- Validation occurs at the contract boundary and again in the application service.
- REST errors follow RFC 9457 Problem Details; SOAP errors are mapped to SOAP faults.
- Correlation IDs flow through responses and structured log context.
- Actuator exposes health probes and Prometheus metrics.

## Circuit breaker on top of outbox retries

The outbox already retries each event with exponential backoff, so a circuit breaker
might look redundant. The two solve different problems. Backoff is per event: during
an outage, every pending event still makes its own failing call. The circuit breaker
is per dependency: once the registry is clearly down, no calls are made at all until a
trial call succeeds.

Two details matter. First, a call rejected by an open circuit is not counted as an
attempt, so an outage longer than the retry budget does not mark good events as
`FAILED`. Second, 4xx responses are excluded from the failure rate, because a rejected
payload is a problem with one event, not with the registry.

## Gateway as a separate service

Spring Cloud Gateway runs on WebFlux, while the API runs on Spring MVC with virtual
threads, so the gateway is a separate deployable rather than a module of the API. That
also matches how it would run in production: one edge service in front of several
backends, scaled independently.

Only reads are retried at the gateway. Retrying a `POST` at the edge could submit a
transaction twice if the first attempt succeeded but the response was lost; the client
owns the `Idempotency-Key` and is the right place to replay writes.

## Tracing through Micrometer

Tracing uses Micrometer Tracing with the OpenTelemetry bridge and the OTLP exporter
instead of the OpenTelemetry Java agent. It keeps configuration in Spring properties,
reuses the observations Spring already creates for HTTP servers, `RestClient`, and the
gateway, and needs no JVM flags. Sampling is configurable so production can trace a
fraction of requests.

