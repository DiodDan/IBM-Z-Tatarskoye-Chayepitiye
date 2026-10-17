# Conversation

Base Kotlin/Spring Boot backend and Vue 3/TypeScript frontend. The project currently contains scaffolding only; conversation endpoints and AI generation are not implemented.

## Prerequisites

- Java 21. Make selects an installed JDK 21 automatically on macOS; on other systems, set `JAVA_HOME` to your JDK 21 installation.
- Node.js 20.19+ or 22.12+ and npm.
- Docker with Docker Compose v2 supporting `--wait`, with Docker running.
- Make. Gradle is downloaded automatically by the included Wrapper.

## First-time setup

Run all Make commands from the repository root:

```sh
make setup
```

This installs frontend dependencies and copies `.env.template` to `.env` only if `.env` is missing. Edit `.env` to set your local PostgreSQL database name, username, password, and port. Existing `.env` values are preserved; the file is ignored by Git.

## Run locally

Start PostgreSQL and wait for its health check:

```sh
make db-up
```

Start the backend in one terminal:

```sh
make backend
```

Start the frontend in a second terminal:

```sh
make frontend
```

| Service | URL |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health |

Vite proxies `/api` requests to the backend. API endpoints are still to be implemented. Ollama is not required to run this scaffold: chat and embedding auto-configuration are disabled.

Stop each development server with Ctrl+C. Stop PostgreSQL with:

```sh
make db-down
```

The named Docker volume preserves database data. Database initialization credentials apply only when the volume is first created; editing `.env` does not change credentials in an existing database.

To follow database logs:

```sh
make db-logs
```

## Build and validate

```sh
make backend-build
make frontend-build
make compose-check
```

The backend command builds the executable JAR and runs any available tests. The frontend command checks TypeScript and creates the production build. The Compose command validates configuration without starting containers. Run `make help` to list commands.

## Java troubleshooting

An immediate Gradle failure showing only `25` means it was launched with Java 25, which is incompatible with this Gradle Kotlin DSL runtime. The Make backend commands select JDK 21 on macOS. To specify a JDK explicitly:

```sh
make backend BACKEND_JAVA_HOME=/path/to/jdk-21
```

When running the Wrapper directly, set `JAVA_HOME` to JDK 21 first. The build toolchain setting controls application compilation but cannot change the Java version that starts Gradle.

## IntelliJ IDEA

Open the repository and import `backend/build.gradle.kts` as a Gradle project. Select Java 21 for the project SDK and Gradle JVM, and use the Gradle Wrapper. Run `ConversationApplicationKt` with the repository root or `backend/` as the working directory, after starting PostgreSQL with `make db-up`. The Make commands can also run in IntelliJ's terminal.

## Configuration and structure

- `backend/`: Gradle Kotlin DSL, Wrapper 8.14.3, Kotlin entry point, application configuration, and empty feature packages.
- `frontend/`: Vue entry point, placeholder view, and directories for components, services, composables, types, and styles.
- `docker-compose.yaml`: PostgreSQL with persistent storage and a health check.
- `.env.template`: tracked configuration example; `.env`: local credentials.
- `Makefile`: setup, development, build, and database commands.

The backend imports `.env` as Java properties from the repository root or its own working directory. `application.yaml` references the same `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_PORT` variables as Compose. Environment variables override file values. When editing the local file, use unquoted values without backslashes or trailing whitespace so both Compose and Spring interpret them consistently.

The backend pins Spring Boot 3.5.15, Spring AI 1.1.8, Kotlin 2.2.21, and Java 21. JPA uses schema validation; Flyway is available but domain migrations are not implemented. When implementing the AI gateway, select an installed `OLLAMA_MODEL`, enable the integration, and add model validation and readiness checks.

The requirements disagree about `updatedAt` and sidebar ordering; resolve that against `openapi.json` before implementing the API.





+ DO NOT USE NORMAL NPM RUN DEV AS IT IS CURRENTLY USING MOCK DATA 
THEREFORE USE 

cd frontend
npm ci
npm run dev:mock