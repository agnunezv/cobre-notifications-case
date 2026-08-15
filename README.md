# Cobre Notifications Case

Webhook notification delivery service developed as part of Cobre's Senior Software Engineer technical case.

## Stack

- Java 21
- Spring Boot 3.5
- Gradle with Groovy DSL
- PostgreSQL
- Flyway
- Testcontainers

## Run locally

Create the local environment file and replace its credential placeholders:

```bash
cp .env.example .env
```

Start PostgreSQL and the application:

```bash
docker compose up -d --wait
./gradlew bootRun
```

Health check: `http://localhost:8080/actuator/health`

## Test

```bash
./gradlew clean check
```
