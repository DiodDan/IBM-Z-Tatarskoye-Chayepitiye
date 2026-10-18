# Conversation

Kotlin/Spring Boot backend and Vue 3/TypeScript frontend for anonymous AI conversations. The backend stores conversation history in PostgreSQL and uses OpenRouter for AI responses.

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

This installs frontend dependencies and copies `.env.template` to `.env` only if `.env` is missing. Edit `.env` to set your PostgreSQL credentials and port, plus `OPENROUTER_API_KEY` and `OPENROUTER_MODEL`. Existing `.env` values are preserved; the file is ignored by Git. Keep the API key private and do not commit it.

## Run locally

Start PostgreSQL and wait for its health check:

```sh
make db-up
```

Start the backend in one terminal:

```sh
make backend
```

`make backend` uses OpenRouter. For local development without an OpenRouter key, run `make backend-echo`; it returns a test reply instead of calling a model.

Start the frontend in a second terminal:

```sh
make frontend
```

| Service | URL |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Backend health | http://localhost:8080/actuator/health |

Vite proxies `/api` requests to the backend. You can also test the backend directly without starting the frontend.

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

- `backend/`: Gradle Kotlin DSL, Wrapper 8.14.3, Kotlin entry point, conversation API, persistence, and OpenRouter integration.
- `frontend/`: Vue entry point, placeholder view, and directories for components, services, composables, types, and styles.
- `docker-compose.yaml`: PostgreSQL with persistent storage and a health check.
- `.env.template`: tracked configuration example; `.env`: local credentials.
- `Makefile`: setup, development, build, and database commands.

The backend imports `.env` as Java properties from the repository root or its own working directory. `application.yaml` references the same `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_PORT` variables as Compose. Environment variables override file values. When editing the local file, use unquoted values without backslashes or trailing whitespace so both Compose and Spring interpret them consistently.

The backend pins Spring Boot 3.5.15, Spring AI 1.1.8, Kotlin 2.2.21, and Java 21. Spring AI connects to OpenRouter's OpenAI-compatible API; `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` are read from the environment or local `.env`. The model must be configured for `make backend`; `make backend-echo` does not require model credentials. JPA uses schema validation and Flyway manages the database schema.

Each assistant message has `suggestions` (follow-up chips). The model is asked in `prompts/system.txt` to end its reply with a `[[suggestions]] a | b | c` line, which `AssistantReplyParser` removes from the stored text. The prompt-risk classifier plugs in by implementing `risk/PromptRiskClassifier.kt` (and removing `UnscoredPromptRiskClassifier`); it returns a score (0–1) and a reason, or `null` when unclassified. They are stored in `message.prompt_risk` / `message.prompt_risk_reason`, together with the model's response time (`message.response_time_ms`). All three are admin-only: the chat API never returns them.

### Admin page

Open http://localhost:5173/admin (or `/#/admin` on hosts without SPA routing). It is read-only and shows every user's conversations. The Responses panel lists every AI reply of every conversation with a permanent app-wide number ("Response №12"), risk score vs. threshold, reason, and response timing ("Pending" until the classifier is plugged in):

- **See full response →** opens the reply's conversation, rings it in the left panel and highlights the reply in the chat.
- **Copy** copies "Response №12"; pasting it (or any text from a reply) into the left search opens the conversation it came from at that reply.
- **Filters** sort all replies on the server: newest, by reason, or by score.

The threshold is `app.admin.risk-threshold` in `application.yaml` (default 0.5). Data comes from `GET /api/admin/conversations[?q=]`, `GET /api/admin/conversations/{id}` and `GET /api/admin/responses?sort=newest|reason|risk-asc|risk-desc`.

**There is no admin login yet.** Anyone who can reach the app can open `/admin` and read all conversations. Add authentication in `admin/AdminController.kt` before deploying anywhere other people can reach.

The requirements disagree about `updatedAt` and sidebar ordering; resolve that against `openapi.json` before implementing the API.

