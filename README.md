# TodoFlow — Full-Stack AI-Powered Productivity SaaS

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot 3.5" />
  <img src="https://img.shields.io/badge/OAuth-2.1%20PKCE-0052CC?style=for-the-badge" alt="OAuth 2.1 PKCE" />
  <img src="https://img.shields.io/badge/MCP-v1.6+-8A2BE2?style=for-the-badge" alt="Model Context Protocol" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?style=for-the-badge&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react" alt="React 19" />
  <img src="https://img.shields.io/badge/TypeScript-5.7-blue?style=for-the-badge&logo=typescript" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Tailwind%20CSS-v4-38B2AC?style=for-the-badge&logo=tailwind-css" alt="Tailwind CSS v4" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker" alt="Docker" />
</p>

**TodoFlow** is a modern, production-grade productivity SaaS platform inspired by Todoist, Notion, and Linear. It combines a modular monolith backend architecture, reactive single-page client design, a high-performance native Android application with local-first SQLite caching, real-time WebSocket collaboration, native generative AI intelligence, an **OAuth 2.1 compliant authorization server**, and a **Model Context Protocol (MCP) server** for AI agent orchestration.

---

## Table of Contents

1. [Key Features](#key-features)
2. [Architecture Overview](#architecture-overview)
3. [Technology Stack](#technology-stack)
4. [Project Structure](#project-structure)
5. [Getting Started](#getting-started)
   - [Prerequisites](#prerequisites)
   - [Environment Configuration](#environment-configuration)
   - [Running with Docker Compose](#running-with-docker-compose)
   - [Running Locally for Development](#running-locally-for-development)
   - [Running the Android App](#running-the-android-app)
6. [Model Context Protocol (MCP) Integration](#model-context-protocol-mcp-integration)
7. [OAuth 2.1 Authorization Server](#oauth-21-authorization-server)
8. [Native AI Capabilities](#native-ai-capabilities)
9. [Database Migrations & Flyway](#database-migrations--flyway)
10. [API & Authentication](#api--authentication)
11. [CI/CD & Deployment](#cicd--deployment)
12. [Modules & License](#modules--license)

---

## Key Features

### 🔌 Model Context Protocol (MCP) Server
- **Standardized AI Integration**: Connect AI agents and assistants (Claude Desktop, Cursor, Gemini, ChatGPT) directly to your TodoFlow workspace using the official `@modelcontextprotocol/sdk`.
- **Dual-Transport Architecture**:
  - **Local Stdio Transport**: Zero-config pipe for local desktop IDEs and CLI agents.
  - **Remote Multi-Session HTTP & SSE**: Multi-tenant streaming via Server-Sent Events (`/sse`) and Streamable HTTP (`/mcp`) for remote agents and web hooks.
- **16 Comprehensive Tools**: Full CRUD over tasks, batch creation, subtask decomposition, project resolution, label assignment, threaded comments, and executive daily briefings.

### 🛡️ Enterprise OAuth 2.1 Authorization Server
- **Strict OAuth 2.1 Compliance**: Implements RFC 6749, RFC 7636, RFC 7009, RFC 7591, and RFC 8414.
- **Mandatory PKCE**: Proof Key for Code Exchange (`code_challenge` / `code_verifier` with `S256` or `plain`) enforced for all clients, eliminating authorization code interception attacks.
- **Dynamic Client Registration (RFC 7591)**: Allows automated agents like ChatGPT Custom GPTs or Claude connectors to dynamically register client credentials.
- **Discovery Metadata (RFC 8414)**: Published at `/.well-known/oauth-authorization-server` and `/.well-known/openid-configuration` for zero-configuration integration.
- **Interactive Consent Screen**: Clean, responsive consent and authentication UI for third-party approval.

### 📱 High-Performance Native Android App
- **Local-First SQLite Architecture**: Built on Kotlin 2.0+ and Jetpack Compose with Write-Ahead Logging (WAL) and B-Tree indexing for instant (<5ms) rendering and offline persistence.
- **Stale-While-Revalidate Sync**: Background synchronization updates cache while keeping the UI responsive with 0ms perceived latency.
- **Nested Subtasks Checklist**: Interactive subtask pill (`1/3 subtasks`), expandable accordion checklist, independent completion toggling, and inline subtask creation and deletion.
- **Task Reordering & Position Retention**: Manual reordering (▲/▼) synced with backend; completion toggling preserves list position without moving tasks to the bottom.
- **Project Lifecycle Management**: Create, edit, color-code, and delete projects directly from the mobile app.
- **Theme & System Polish**: System / Light / Dark theme switcher, edge-to-edge layout with full dark mode status bar contrast, and Android 12+ adaptive launcher and splash screen icons.

### 📋 Task & Project Management (Web)
- **Workspace-First Experience**: Clean, distraction-free dashboard with dedicated sidebar navigation, custom projects, color accents, and task count badges.
- **Hierarchical Subtasks & Checklists**: Decompose tasks into ordered checklist items with live visual progress bars.
- **Recurring Tasks Engine**: Automatic next-instance scheduling upon task completion supporting Daily, Weekly, Monthly, and Yearly intervals with interval steppers and forward-rolling due dates.
- **Rich Markdown Notes**: Dual-mode Write/Preview Markdown editor with full GitHub Flavored Markdown support, code blocks, headers, and bulleted lists.
- **Priority & Due Date Engine**: P1 (Urgent), P2 (High), P3 (Medium), and P4 (Low) visual badges, overdue date alerts, and drag-and-drop position sorting (`@dnd-kit`).
- **Project Lifecycle Management**: Dedicated project creation, editing, color customization, and full project deletion with safe confirmation dialogs.

### 🧠 Native AI Productivity Suite (Spring AI + Google Gemini)
- **Natural Language Task Parsing**: Transform unstructured thoughts (e.g., *"Finish quarterly tax filing by next Thursday priority high"*) into structured tasks with title, due date, project, and priority.
- **One-Click Subtask Decomposition**: Automatically break down complex or vague tasks into 4–5 actionable subtask checklist items.
- **AI Smart Daily Planner (Eisenhower Matrix)**: Prioritize your day into an interactive 2x2 Eisenhower Matrix (*Do First*, *Schedule*, *Delegate*, *Eliminate*) paired with AI reasoning and focus time blocks.
- **Productivity Coach**: Receive contextual analysis of completion velocity, bottleneck projects, and actionable work habits.

### ⚡ Real-Time Collaboration & Synchronization
- **WebSocket / STOMP Updates**: Immediate push notifications across open client sessions when tasks are created, updated, reordered, or completed.
- **Multi-User Isolation**: User-scoped WebSocket topic channels (`/topic/users/{userId}/*`) preventing data leakage across accounts.
- **Live Connection Monitor**: Real-time WebSocket connection state indicator with automatic reconnection logic and manual retry controls.
- **Activity Stream Drawer**: Comprehensive activity feed tracking created, completed, and modified items in a slide-out drawer.
- **Optimistic UI Mutations**: Instant client updates powered by TanStack Query with automated cache rollback and toast notifications on network failure.

---

## Architecture Overview

TodoFlow is engineered as an extensible **modular monolith** with clean boundary separation:

```text
┌───────────────────────┐  ┌───────────────────────┐  ┌───────────────────────┐
│       Web Client      │  │   Native Android App  │  │ AI Agents (MCP / IDE) │
│ React 19 + TypeScript │  │   Kotlin 2.0+ Compose │  │ Claude, Cursor, GPTs  │
└───────────┬───────────┘  └───────────┬───────────┘  └───────────┬───────────┘
            │                          │                          │
   HTTPS / WebSocket / REST            │ HTTPS / REST             │ Stdio / SSE / Streamable HTTP
            │                          │                          │
            ▼                          ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TodoFlow Platform Ingress                          │
│                      Nginx Reverse Proxy / TLS Gateway                      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
       ┌───────────────────────────────┴───────────────────────────────┐
       ▼                                                               ▼
┌─────────────────────────────────────────┐  ┌────────────────────────────────┐
│         Spring Boot 3.5 Backend         │  │     TodoFlow Node.js MCP Server│
│                                         │  │     (@modelcontextprotocol/sdk)│
│ ┌────────────────┐  ┌─────────────────┐ │  │                                │
│ │  OAuth 2.1     │  │  Tasks & Recur  │ │  │ ┌────────────────────────────┐ │
│ │  PKCE Engine   │  │  Engine         │ │  │ │ 16 Productivity Tools     │ │
│ └────────────────┘  └─────────────────┘ │  │ │ - Tasks CRUD, Batch, Recur│ │
│ ┌────────────────┐  ┌─────────────────┐ │  │ │ - Subtasks & Checklists   │ │
│ │  API Key Auth  │  │  Projects (CRUD)│ │  │ │ - Projects, Labels, Brief │ │
│ └────────────────┘  └─────────────────┘ │  │ └────────────────────────────┘ │
│ ┌────────────────┐  ┌─────────────────┐ │  │  Stdio / SSE / Streamable HTTP │
│ │ Realtime STOMP │  │ Spring AI Gemini│ │  └────────────────┬───────────────┘
│ └────────────────┘  └─────────────────┘ │                   │
└──────────────────┬──────────────────────┘                   │ REST (X-API-Key / Bearer)
                   │                                          │
                   ├──────────────────────────────────────────┘
                   ▼
       ┌───────────────────────┐             ┌───────────────────────┐
       │ PostgreSQL 16 (Flyway)│             │   Google Gemini API   │
       │ Tasks, Projects, Users│             │  Flash 3.5 GenAI LLM  │
       └───────────────────────┘             └───────────────────────┘
```

---

## Technology Stack

| Layer | Technologies |
|---|---|
| **Backend** | Java 21, Spring Boot 3.5.x, Spring Data JPA, Spring Security, Hibernate, Spring Events, Flyway |
| **OAuth 2.1** | RFC 6749, RFC 7636 (PKCE), RFC 7009 (Revocation), RFC 7591 (Registration), RFC 8414 (Metadata) |
| **MCP Server** | Node.js 20+, TypeScript, `@modelcontextprotocol/sdk`, Express, Zod, Axios |
| **Android App** | Kotlin 2.0+, Jetpack Compose, Material Design 3, SQLite OpenHelper (WAL), StateFlow, Coroutines, Retrofit |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS v4, Zustand, TanStack Query, Framer Motion, dnd-kit |
| **Realtime** | WebSocket, STOMP, SockJS |
| **AI Integration** | Spring AI, Google GenAI (`gemini-3.5-flash`), Model Context Protocol |
| **Database** | PostgreSQL 16 (compatible with local PostgreSQL & cloud providers like Supabase) |
| **DevOps & Infra** | Docker, Docker Compose, Nginx, Let's Encrypt TLS, GitHub Actions CI/CD |

---

## Project Structure

```text
todo-app/
├── backend/                       # Spring Boot 3.5 application
│   ├── src/main/java/com/example/todo/
│   │   ├── auth/                 # JWT authentication & security filters
│   │   ├── oauth/                # OAuth 2.1 Authorization Server (PKCE, Discovery, Tokens)
│   │   ├── task/                 # Tasks, recurring engine, subtasks
│   │   ├── project/              # Project organization & management
│   │   ├── label/                # Label entities & tagging
│   │   ├── activity/             # Event-driven audit log & activities
│   │   ├── apikey/               # API key generation & authentication
│   │   ├── ai/                   # Spring AI & Gemini integration
│   │   └── realtime/             # WebSocket handlers & presence
│   └── src/main/resources/
│       ├── db/migration/         # Flyway SQL migrations (V1 .. V12)
│       └── application.yaml      # Configuration profiles
├── mcp-server/                    # Model Context Protocol (MCP) Server
│   ├── src/
│   │   └── index.ts              # 16 MCP Tools + Stdio, SSE, and Streamable HTTP Transports
│   ├── package.json
│   └── Dockerfile                # Multi-stage container build
├── android/                       # Native Android application
│   ├── app/src/main/
│   │   ├── java/com/example/todoflow/
│   │   │   ├── data/             # SQLite OpenHelper (WAL), entities, SessionManager, Retrofit
│   │   │   ├── theme/            # Material 3 colors, typography, dynamic theming
│   │   │   └── ui/               # Compose screens: Login, Tasks, Dialogs, Rows
│   │   └── res/                  # Adaptive icons, splash screen, string resources
│   └── build.gradle.kts          # Gradle build configuration
├── frontend/                      # React 19 + TypeScript + Vite SPA
│   ├── src/
│   │   ├── api/                  # Axios HTTP client & interceptors
│   │   ├── components/           # UI primitives, layout, modals, drawers
│   │   ├── features/             # Feature slices (tasks, projects, AI, etc.)
│   │   ├── store/                # Zustand client state stores
│   │   └── realtime/             # STOMP socket client & lifecycle hooks
│   └── vite.config.ts
├── infra/                         # Infrastructure & reverse proxy
│   └── nginx/                    # Nginx reverse proxy configuration
├── .github/workflows/             # GitHub Actions CI/CD workflows
└── docker-compose.yml             # Local and production container orchestration
```

---

## Getting Started

### Prerequisites

Ensure you have installed:
- **Java 21 JDK**
- **Node.js 20+** and **npm**
- **Docker & Docker Compose**
- **Maven 3.9+** (or use the included wrapper `./mvnw`)
- **Android Studio** (Ladybug or newer with Android SDK 35) for mobile development
- A **Google Gemini API Key** (from [Google AI Studio](https://aistudio.google.com/))

---

### Environment Configuration

Create a root `.env` file:

```env
# Database Settings
POSTGRES_DB=todoapp
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
DB_URL=jdbc:postgresql://postgres:5432/todoapp
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Security & AI
JWT_SECRET=your_super_secret_jwt_key_with_at_least_256_bits_length
GEMINI_API_KEY=your_gemini_api_key_here

# MCP Settings (Optional for Docker)
MCP_PORT=3001
MCP_TRANSPORT=sse
```

---

### Running with Docker Compose

To start the database, backend, frontend, and remote MCP server together:

```bash
docker-compose up -d
```

- **Web Application**: `http://localhost/`
- **Backend API**: `http://localhost/api/v1`
- **WebSocket Gateway**: `http://localhost/ws`
- **MCP Server (Streamable HTTP)**: `http://localhost:3001/mcp`
- **MCP Server (SSE Handshake)**: `http://localhost:3001/sse`

---

### Running Locally for Development

#### 1. Start Database
```bash
docker run -d \
  --name todo-postgres \
  -e POSTGRES_DB=todoapp \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

#### 2. Start Backend
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 3. Start Frontend
```bash
cd frontend
npm install
npm run dev
```

#### 4. Start MCP Server (Local Stdio or HTTP)
```bash
cd mcp-server
npm install
npm run build

# Run in Stdio mode for Claude/Cursor:
TODOFLOW_BASE_URL=http://localhost:8080/api/v1 TODOFLOW_API_KEY=your_key node dist/index.js

# Or run in Remote SSE / Streamable HTTP mode:
MCP_TRANSPORT=sse PORT=3001 npm start
```

---

### Running the Android App

1. Open **Android Studio** and select **Open** -> choose `android/`.
2. Wait for Gradle sync to complete.
3. Configure the backend URL in the login screen:
   - **Android Emulator**: `http://10.0.2.2:8080/`
   - **Physical Device**: `http://<your-lan-ip>:8080/`
4. Select your device and click **Run** (`Shift + F10`).
5. Run unit tests:
   ```bash
   cd android
   ./gradlew test
   ```

---

## Model Context Protocol (MCP) Integration

TodoFlow includes a first-class Model Context Protocol server located in `mcp-server/`. It equips LLM agents with tools to observe, manage, and coordinate your tasks.

### Registered MCP Tools (16)

| Tool Name | Description |
|---|---|
| `todoflow_create_task` | Create a task with priority, due date, project, recurrence, and subtasks. |
| `todoflow_create_tasks_batch` | Bulk create multiple tasks at once. |
| `todoflow_list_tasks` | Query tasks with filtering by project, completion status, due date, or search term. |
| `todoflow_get_task` | Retrieve complete task details including description, subtasks, labels, and comments. |
| `todoflow_update_task` | Modify title, description, priority, due date, or project of an existing task. |
| `todoflow_delete_task` | Permanently delete a task. |
| `todoflow_complete_task` | Mark a task as completed (triggers recurring task engine if applicable). |
| `todoflow_add_subtasks` | Add checklist subtasks under a task. |
| `todoflow_toggle_subtask` | Toggle completion status of an individual subtask. |
| `todoflow_list_projects` | Retrieve all projects with their colors and task counts. |
| `todoflow_create_project` | Create a new project with custom name and color hex. |
| `todoflow_list_labels` | Retrieve all labels in the user's workspace. |
| `todoflow_create_label` | Create a new label with custom color. |
| `todoflow_add_comment` | Post a discussion comment or note to a task. |
| `todoflow_list_comments` | Retrieve threaded comments on a task. |
| `todoflow_daily_briefing` | Generate an executive daily summary of overdue, today's, and high-priority tasks. |

### Connecting to Claude Desktop

Add this entry to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "todoflow": {
      "command": "node",
      "args": ["<PATH_TO_TODO_APP>/mcp-server/dist/index.js"],
      "env": {
        "TODOFLOW_BASE_URL": "http://localhost:8080/api/v1",
        "TODOFLOW_API_KEY": "todo_live_your_api_key_here"
      }
    }
  }
}
```

### Connecting to Cursor

Navigate to **Cursor Settings → Features → MCP Servers** and click **Add New MCP Server**:
- **Name**: `todoflow`
- **Type**: `command`
- **Command**:
  ```bash
  TODOFLOW_BASE_URL=http://localhost:8080/api/v1 TODOFLOW_API_KEY=todo_live_... node <PATH_TO_TODO_APP>/mcp-server/dist/index.js
  ```

---

## OAuth 2.1 Authorization Server

TodoFlow incorporates a built-in **OAuth 2.1 compliant authorization server** implemented in `backend/src/main/java/com/example/todo/oauth/`. It enables secure, delegated access for third-party AI agents (such as custom ChatGPT GPTs, Claude Web, and automation scripts) without exposing user passwords.

### Security Highlights
- **PKCE Required (RFC 7636)**: Authorization code requests require `code_challenge` (`S256` or `plain`) and `code_challenge_method`.
- **Dynamic Client Registration (RFC 7591)**: Automated agents can register themselves dynamically via `POST /oauth2/register`.
- **Metadata Discovery (RFC 8414)**: Standards-compliant metadata published at `/.well-known/oauth-authorization-server` and `/.well-known/openid-configuration`.
- **Token Revocation (RFC 7009)**: Secure token revocation via `POST /oauth2/revoke`.

### OAuth 2.1 Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/.well-known/oauth-authorization-server` | `GET` | RFC 8414 Server Discovery metadata |
| `/.well-known/openid-configuration` | `GET` | OpenID Connect discovery document |
| `/oauth2/authorize` | `GET` | Interactive authorization login & consent screen |
| `/oauth2/authorize` | `POST` | Processes consent and issues authorization code |
| `/oauth2/token` | `POST` | Exchanges authorization code & PKCE verifier for JWT |
| `/oauth2/revoke` | `POST` | Revokes an access or refresh token (RFC 7009) |
| `/oauth2/register` | `POST` | Dynamic client registration (RFC 7591) |

---

## Native AI Capabilities

TodoFlow integrates directly with Google Gemini through Spring AI, providing intelligent automation while keeping credentials safely protected on the backend:

- **Natural Language Parsing (`POST /api/v1/ai/parse-task`)**: Parses raw text descriptions into structured tasks with title, description, priority (`LOW`, `MEDIUM`, `HIGH`), and localized due date.
- **Checklist Breakdown (`POST /api/v1/ai/decompose-task`)**: Analyzes high-level tasks and automatically generates ordered subtask checklist items.
- **Smart Daily Planner (`POST /api/v1/ai/smart-planner`)**: Evaluates urgent and important tasks, places them into the 4 Eisenhower quadrants, and drafts structured focus blocks for the day.
- **Productivity Coach (`POST /api/v1/ai/productivity-coach`)**: Generates tailored insights and habit recommendations based on recent activity velocity.

---

## Database Migrations & Flyway

Database schema changes are tracked with versioned SQL migrations located in `backend/src/main/resources/db/migration/`:

- `V1`: Users & roles schema
- `V2`: Projects table
- `V3`: Tasks table with priority & status
- `V4`: Labels & task-label associations
- `V5`: Refresh tokens for JWT rotation
- `V6`: Drag-and-drop position sorting
- `V7`: Activity feed & audit logging
- `V8`: Subtasks & checklists
- `V9`: Rich Markdown notes
- `V10`: Recurring tasks engine configuration
- `V11`: User API keys with SHA-256 hashes
- `V12`: User timezone preferences & UTC conversion

---

## API & Authentication

TodoFlow supports three flexible authentication mechanisms:
1. **JWT Bearer Authentication**: Short-lived access tokens with automatic refresh token rotation (`/api/v1/auth/login`, `/api/v1/auth/refresh`).
2. **OAuth 2.1 Bearer Tokens**: Standard authorization code flow with PKCE for third-party tools (`/oauth2/*`).
3. **API Key Authentication**: Sent via `X-API-Key: todo_live_...` header for scripts, background automations, and MCP.

### Key API Endpoints
- `POST /api/v1/auth/register` — Register a new user
- `POST /api/v1/auth/login` — Authenticate and receive JWT + Refresh Token
- `GET /api/v1/tasks` — List tasks with filtering and pagination
- `POST /api/v1/tasks` — Create task
- `PATCH /api/v1/tasks/{id}/complete` — Complete task (handles recurrence)
- `POST /api/v1/tasks/{id}/subtasks` — Add checklist subtask
- `GET /api/v1/projects` — List user projects
- `PUT /api/v1/projects/{id}` — Update project name and color
- `DELETE /api/v1/projects/{id}` — Delete project
- `POST /api/v1/ai/parse-task` — Natural language task parser
- `POST /api/v1/ai/smart-planner` — AI Eisenhower Matrix daily planner
- `POST /api/v1/ai/productivity-coach` — Productivity habits analysis
- `GET /api/v1/api-keys` — List active user API keys
- `POST /api/v1/api-keys` — Generate a new API key

---

## CI/CD & Deployment

- **GitHub Actions Workflows**: Automated linting, test suites, and Docker image builds (`ghcr.io/ajaypds/todo-backend`, `ghcr.io/ajaypds/todo-frontend`, `ghcr.io/ajaypds/todo-mcp-server`).
- **Production Deployment**: Runs on AWS EC2 with Nginx reverse proxy, automatic SSL via Let's Encrypt, and Dynamic DNS.
- **Production Profiles**: Supports cloud databases (such as Supabase PostgreSQL) via `application-prod.yaml` and environment variables.

---

## Modules & License

- **Backend (`backend/`)**: Spring Boot 3.5 application source, JPA entities, OAuth 2.1 server, and Flyway migrations.
- **MCP Server (`mcp-server/`)**: Model Context Protocol server exposing 16 productivity tools via stdio, SSE, and Streamable HTTP.
- **Android (`android/`)**: Native Kotlin 2.0+ Jetpack Compose mobile app with local-first SQLite database.
- **Frontend (`frontend/`)**: React 19 SPA, Tailwind CSS v4 components, Zustand stores, and TanStack queries.

This project is licensed under the MIT License.
