# Event Gateway API

Spring Boot Event Gateway API for a financial Event Ledger system.

The gateway accepts transaction events from upstream systems, stores each event
idempotently, and synchronously calls an internal Account Service to apply the
transaction. Events remain queryable even when Account Service is unavailable.

## Baseline Stack

- Java 21
- Spring Boot 4.0.6
- Spring Cloud 2025.1.1 with Netflix Eureka client
- Zipkin tracing through Spring Boot Actuator and Micrometer tracing
- Docker deployment
- Lombok
- Apache Commons Collections
- Resilience4j circuit breaker and bulkhead
- H2 in-memory database

## API

- `POST /events` submits a transaction event.
- `GET /events/{id}` retrieves one event by event id.
- `GET /events?account={accountId}` lists events for an account ordered by original event timestamp.
- `GET /health` returns the public gateway health response.
- `GET /api/v1/hello` returns legacy service metadata and confirms that the service is running.
- `GET /actuator/health` returns runtime health details.
- `GET /h2-console` opens the development H2 console.

### Event Submission

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

Submission behavior:

- New event applied successfully: `201 Created`, status `APPLIED`.
- Exact duplicate `eventId` and payload: `200 OK`, `duplicate=true`, transaction is not re-applied.
- Same `eventId` with different payload: `409 Conflict`.
- Account Service unavailable: event is stored, `202 Accepted`, status `APPLY_FAILED`.
- Account event listing is ordered by `eventTimestamp`, not arrival order.

### Response Envelope

Successful Event Gateway responses use a standard envelope:

```json
{
  "timestamp": "2026-06-06T21:00:00Z",
  "status": 201,
  "code": "EVENT_CREATED",
  "description": "Event was stored and applied to the account.",
  "data": {
    "eventId": "evt-001",
    "accountId": "acct-123",
    "status": "APPLIED"
  }
}
```

Error responses use a standard error envelope:

```json
{
  "timestamp": "2026-06-06T21:00:00Z",
  "status": 409,
  "code": "DUPLICATE_EVENT_CONFLICT",
  "description": "Event id already exists with a different payload: evt-001",
  "details": []
}
```

Primary event response codes:

- `EVENT_CREATED`: event was stored and applied.
- `EVENT_DUPLICATE`: exact duplicate event was ignored and existing event returned.
- `EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED`: event was stored but Account Service apply failed.
- `EVENT_RETRIEVED`: event lookup succeeded.
- `EVENTS_LISTED`: account event list succeeded.
- `HEALTH_OK`: public health check succeeded.

Primary error codes:

- `VALIDATION_ERROR`: request validation failed.
- `MALFORMED_REQUEST`: request body could not be parsed or has invalid field values.
- `EVENT_NOT_FOUND`: requested event id does not exist.
- `DUPLICATE_EVENT_CONFLICT`: event id already exists with a different payload.
- `INTERNAL_SERVER_ERROR`: unexpected server-side failure.

## Service Discovery

This service is configured as a Eureka client. On startup it registers itself
with the Eureka registry using `spring.application.name`, which defaults to
`event-gateway-api`.

Default Eureka registry URL:

```powershell
$env:EUREKA_DEFAULT_ZONE = "http://localhost:8761/eureka/"
```

Useful Eureka environment overrides:

```powershell
$env:EUREKA_CLIENT_ENABLED = "true"
$env:EUREKA_REGISTER_WITH_EUREKA = "true"
$env:EUREKA_FETCH_REGISTRY = "true"
$env:EUREKA_INSTANCE_PREFER_IP_ADDRESS = "true"
```

The gateway resolves Account Service from Eureka before calling:

- `POST /accounts/{accountId}/transactions`

Default Account Service registry id:

```powershell
$env:ACCOUNT_SERVICE_SERVICE_ID = "account-service"
```

The Account Service must register with Eureka using the same service id. If
Eureka has no healthy `account-service` instance, the gateway still persists
the event and returns `202 Accepted` with status `APPLY_FAILED`.

## Local Build

Use a Java 21 JDK before running Maven.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean test
mvn spring-boot:run
```

## Docker

Build the executable Spring Boot jar first. The jar contains the application
classes and runtime dependencies, and the Docker image only copies that
pre-built executable.

```powershell
mvn clean package
docker compose up --build
```

Zipkin UI is available at `http://localhost:9411`.
