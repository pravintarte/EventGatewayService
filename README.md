# Event Gateway API

Spring Boot skeleton for the Event Gateway API microservice.

## Baseline Stack

- Java 21
- Spring Boot 4.0.6
- Zipkin tracing through Spring Boot Actuator and Micrometer tracing
- Docker deployment
- Lombok
- Apache Commons Collections
- Resilience4j circuit breaker and bulkhead
- H2 in-memory database

## API

- `GET /api/v1/hello` returns service metadata and confirms that the service is running.
- `GET /actuator/health` returns runtime health details.
- `GET /h2-console` opens the development H2 console.

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
