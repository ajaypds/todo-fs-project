# TodoFlow backend — Go + Gin

A drop-in replacement for the Spring Boot backend in `../backend`. It talks to the **same PostgreSQL
database**, exposes the **same REST / WebSocket / OAuth endpoints with the same responses**, and has the
**MCP server built in** (no separate Node service needed). It starts in a few milliseconds, which is the
point: Cloud Run cold starts become negligible.

The Spring Boot backend is untouched; both can run side by side against one database (JWTs, refresh
tokens, API keys and bcrypt hashes created by one are accepted by the other).

## Configuration — identical environment variables

| Variable | Meaning |
| --- | --- |
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://host:5432/todo?sslmode=require` (a `postgres://` URL also works; `host=/cloudsql/...` sockets are supported) |
| `DB_USERNAME`, `DB_PASSWORD` | database credentials |
| `JWT_SECRET` | HMAC secret (≥ 32 bytes; algorithm HS256/384/512 is chosen by length like JJWT does) |
| `GEMINI_API_KEY` | Gemini key for `/api/v1/ai/*` |
| `MAIL_FROM`, `MAIL_USERNAME`, `MAIL_PASSWORD` | accepted for compatibility (verification codes are logged, as in the Java backend) |
| `PORT` | listen port (Cloud Run sets it; default `8080`) |
| `GEMINI_BASE_URL` | optional, overrides the Gemini API host (tests) |

The schema is owned by Flyway in the Java backend; the Go server never migrates. Run the Java backend
(or Flyway) once against a new database before pointing the Go backend at it.

## Run

```bash
cd backend-go
export DB_URL=jdbc:postgresql://localhost:5432/todo DB_USERNAME=todo DB_PASSWORD=todo \
       JWT_SECRET=$(openssl rand -hex 32) GEMINI_API_KEY=...
go run ./cmd/server
go test ./...
```

## Deploy to Cloud Run

`Dockerfile` builds a static binary into a distroless image. Point the Cloud Build trigger (or
`gcloud run deploy --source backend-go`) at this directory and keep the environment variables you already
use for the Java service. The default 512 MiB / 1 vCPU is plenty.

## Built-in MCP server

Same tools (`todoflow_*`, 16 of them), schemas and messages as `../mcp-server`, served by this process:

| Route | Purpose |
| --- | --- |
| `POST/GET/DELETE /mcp` (also `/mcp/streamable`, `/streamable`) | Streamable HTTP (JSON responses, `Mcp-Session-Id`) |
| `GET /sse`, `POST /message` (also under `/mcp/…`) | legacy HTTP+SSE |
| `GET /health`, `GET /info`, `GET /` | discovery |
| `/.well-known/oauth-*`, `/oauth2/*` | OAuth 2.1 (served natively; the Node proxy is no longer needed) |

Authenticate with `X-API-Key`, `Authorization: Bearer <api key or JWT>`, or `?apiKey=`. Tools run by calling
the REST API in-process with the caller's credentials, so permissions and error messages are those of the
REST endpoints. Unlike the Node server, `todoflow_complete_task` uses `PUT /api/v1/tasks/{id}` (the REST API
has no `PATCH` for tasks, so the Node tool failed against the Java backend).

## How parity was verified

Both backends were run against identical migrated databases and the same ~450 scenario requests were
replayed (auth, users, tasks, subtasks, projects, labels, comments, activities, workspaces, API keys,
OAuth, CORS, error cases): status, body and relevant headers are identical. The STOMP/SockJS endpoint
(`/ws`) was compared frame by frame, and the MCP tools/transports against the Node server.

Known, intentional differences:

* `GET /` now returns the MCP discovery document (Java answered 500); `/mcp`, `/sse`, `/message`,
  `/health`, `/info` are public routes that authenticate themselves.
* SockJS: WebSocket and `/ws/info` are supported; the HTTP-streaming/polling fallbacks are not.
* MCP sessions live in memory (like the Node server): with several Cloud Run instances a client may need to
  re-initialise. Using a deleted MCP session answers a clean `404` (Node had a bug there).
* Java quirks are preserved on purpose where clients could depend on them (for example the 409 when a
  task's auto-created project already exists, and the randomised choice between validation messages).
