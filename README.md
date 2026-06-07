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
- `GET /accounts/{accountId}/balance` proxies an Account Service balance lookup.
- `GET /accounts/{accountId}` proxies Account Service account details and recent transactions.
- `GET /health` returns the public gateway health response.
- `GET /api/v1/hello` returns legacy service metadata and confirms that the service is running.
- `GET /actuator/health` returns runtime health details.
- `GET /h2-console` opens the development H2 console.

### Event Submission

```json
{
  "eventId": "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
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
- Account Service rejects the transaction with a non-retryable client error: event is stored, `202 Accepted`, status `APPLY_REJECTED`.
- Retryable Account Service apply failures are retried by a scheduled gateway worker with backoff.
- Account event listing is ordered by `eventTimestamp`, not arrival order.

### Account Read-Through

The gateway is the external boundary for Account Service reads. These endpoints
call Account Service internally and return the downstream response body and HTTP
status to the caller.

- `GET /accounts/{accountId}/balance`: current account balance.
- `GET /accounts/{accountId}`: account details and recent transactions.

If Account Service is unavailable, the gateway returns `503 Service Unavailable`
with code `ACCOUNT_SERVICE_UNAVAILABLE`.

### Response Envelope

Successful Event Gateway responses use a standard envelope:

```json
{
  "timestamp": "2026-06-06T21:00:00Z",
  "status": 201,
  "code": "EVENT_CREATED",
  "description": "Event was stored and applied to the account.",
  "data": {
    "eventId": "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
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
  "description": "Event id already exists with a different payload: 9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2",
  "details": []
}
```

Primary event response codes:

- `EVENT_CREATED`: event was stored and applied.
- `EVENT_DUPLICATE`: exact duplicate event was ignored and existing event returned.
- `EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED`: event was stored but Account Service apply failed.
- `EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED`: event was stored but Account Service rejected the transaction.
- `EVENT_RETRIEVED`: event lookup succeeded.
- `EVENTS_LISTED`: account event list succeeded.
- `HEALTH_OK`: public health check succeeded.

Primary error codes:

- `VALIDATION_ERROR`: request validation failed.
- `MALFORMED_REQUEST`: request body could not be parsed or has invalid field values.
- `EVENT_NOT_FOUND`: requested event id does not exist.
- `DUPLICATE_EVENT_CONFLICT`: event id already exists with a different payload.
- `ACCOUNT_SERVICE_UNAVAILABLE`: Account Service could not be reached for a read-through request.
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
- `GET /accounts/{accountId}/balance`
- `GET /accounts/{accountId}`

Default Account Service registry id:

```powershell
$env:ACCOUNT_SERVICE_SERVICE_ID = "account-service"
```

Default Account Service fallback URL:

```powershell
$env:ACCOUNT_SERVICE_DEFAULT_URL = "http://localhost:8081"
```

Account Service timeout overrides:

```powershell
$env:ACCOUNT_SERVICE_CONNECT_TIMEOUT = "2s"
$env:ACCOUNT_SERVICE_READ_TIMEOUT = "5s"
```

Failed apply retry overrides:

```powershell
$env:EVENT_GATEWAY_RETRY_ENABLED = "true"
$env:EVENT_GATEWAY_RETRY_FIXED_DELAY = "30s"
$env:EVENT_GATEWAY_RETRY_INITIAL_DELAY = "30s"
```

The Account Service should register with Eureka using the same service id. The
gateway uses Eureka only to resolve the Account Service base URL; the concrete
endpoint path is built by the Account Service client. If Eureka is unavailable
or has no healthy `account-service` instance, the gateway uses
`ACCOUNT_SERVICE_DEFAULT_URL`. If the fallback URL is also unavailable, the
gateway still persists the event and returns `202 Accepted` with status
`APPLY_FAILED`. Apply calls include the event id as an `Idempotency-Key` header
so Account Service can safely deduplicate gateway retries.

## Structured Logging

Console logs are emitted as JSON using Spring Boot structured Logstash format.
Each log line includes `timestamp`, `level`, `serviceName`, `traceId`, `spanId`,
`logger`, `thread`, and `message`.

`serviceName` is populated from `spring.application.name`. `traceId` and
`spanId` are populated from the tracing MDC when available; outside a traced
request they are emitted as empty strings so downstream log queries can rely on
stable fields.

## Acceptance Tests

Cucumber acceptance tests run through Maven using the JUnit Platform. They cover
event submission happy path, validation failure, duplicate idempotency,
duplicate conflict handling, out-of-order event listing, and Account Service
unavailable behavior.

Run only the Cucumber acceptance suite:

```powershell
mvn -Dtest=RunCucumberAcceptanceTest test
```

Run all unit, integration, and acceptance tests:

```powershell
mvn clean test
```

Published test results:

- Maven Surefire reports: `target/surefire-reports`
- Cucumber HTML report: `target/cucumber-reports/cucumber.html`
- Cucumber JSON report: `target/cucumber-reports/cucumber.json`
- Cucumber JUnit XML report: `target/cucumber-reports/cucumber.xml`

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
