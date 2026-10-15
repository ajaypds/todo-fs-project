# TodoFlow — Full-Stack AI-Powered Productivity SaaS

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot 3.5" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Android-Jetpack%20Compose-3DDC84?style=for-the-badge&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react" alt="React 19" />
  <img src="https://img.shields.io/badge/TypeScript-5.7-blue?style=for-the-badge&logo=typescript" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Tailwind%20CSS-v4-38B2AC?style=for-the-badge&logo=tailwind-css" alt="Tailwind CSS v4" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker" alt="Docker" />
</p>

**TodoFlow** is a modern, production-grade productivity SaaS platform inspired by Todoist, Notion, and Linear. It combines a modular monolith backend architecture, reactive single-page client design, a high-performance native Android application with local-first SQLite caching, real-time WebSocket collaboration, and native generative AI intelligence into a cohesive workflow.

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
6. [Native AI Capabilities](#native-ai-capabilities)
7. [Database Migrations & Flyway](#database-migrations--flyway)
8. [API & Authentication](#api--authentication)
9. [CI/CD & Deployment](#cicd--deployment)
10. [Modules & License](#modules--license)

---

## Key Features

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

### 🎨 Modern UI & Ergonomics
- **Command Palette (`Ctrl + K` / `Cmd + K`)**: Instant keyboard navigation, task search, project switching, and action execution.
- **Keyboard Shortcuts**: Rapid task creation hotkey (`N`), quick escape dismissal, and accessible tab traversal.
- **Responsive Mobile Navigation**: Collapsible sidebar, mobile bottom navigation bar with quick-add action button, and touch-friendly drag-to-dismiss bottom sheets.
- **CSS-First Theming**: Full dark mode and light mode support built on Tailwind CSS v4 design tokens.
- **Timezone Awareness**: Timestamp persistence standardized to UTC `Instant` with automatic browser timezone localization.

---

## Architecture Overview

TodoFlow is engineered as an extensible **modular monolith** with clean boundary separation:

```text
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│           Web Client            │       │       Native Android App        │
│ React 19 + TypeScript + Tailwind│       │   Kotlin + Compose + SQLite WAL │
└────────────────┬────────────────┘       └────────────────┬────────────────┘
                 │                                         │
        HTTPS / REST / WebSocket                           │ HTTPS / REST
                 │                                         │
                 ▼                                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot 3.5 Backend                          │
│                                                                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────────┐  │
│  │   Auth & RBAC    │  │  Tasks & Recur   │  │ Projects (CRUD + Color) │  │
│  └──────────────────┘  └──────────────────┘  └─────────────────────────┘  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────────┐  │
│  │  Activity Stream │  │ Realtime (STOMP) │  │  API Keys & Subtasks    │  │
│  └──────────────────┘  └──────────────────┘  └─────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Spring AI / Google Gemini 3.5 Integration                           │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                           │
│  Application Events (Decoupled Domain Event Bus)                          │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     │
                  ┌──────────────────┴──────────────────┐
                  ▼                                     ▼
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
| **Android App** | Kotlin 2.0+, Jetpack Compose, Material Design 3, SQLite OpenHelper (WAL), StateFlow, Coroutines, Retrofit, Kotlinx Serialization |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS v4, Zustand, TanStack Query, Framer Motion, dnd-kit |
| **Realtime** | WebSocket, STOMP, SockJS |
| **AI Integration** | Spring AI, Google GenAI (`gemini-3.5-flash`) |
| **Database** | PostgreSQL 16 (compatible with local PostgreSQL & cloud providers like Supabase) |
| **DevOps & Infra** | Docker, Docker Compose, Nginx, Let's Encrypt TLS, GitHub Actions CI/CD |

---

## Project Structure

```text
todo-app/
├── backend/                       # Spring Boot 3.5 application
│   ├── src/main/java/com/example/todo/
│   │   ├── auth/                 # JWT authentication & security filters
│   │   ├── task/                 # Tasks, recurring engine, subtasks
│   │   ├── project/              # Project organization & management
│   │   ├── label/                # Label entities & tagging
│   │   ├── activity/             # Event-driven audit log & activities
│   │   ├── apikey/               # API key generation & authentication
│   │   ├── ai/                   # Spring AI & Gemini integration
│   │   └── realtime/             # WebSocket handlers & presence
│   └── src/main/resources/
│       ├── db/migration/         # Flyway SQL migrations (V1 .. V12)
│       ├── application.yaml      # Common configuration
│       ├── application-dev.yaml  # Development profile
│       └── application-prod.yaml # Production profile
├── android/                       # Native Android application
│   ├── app/src/main/
│   │   ├── java/com/example/todoflow/
│   │   │   ├── data/
│   │   │   │   ├── local/        # SQLite OpenHelper (WAL), entities, SessionManager
│   │   │   │   ├── model/        # Kotlinx Serialization DTOs
│   │   │   │   ├── remote/       # Retrofit API interface & client
│   │   │   │   └── repository/   # Offline-first repository with optimistic updates
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
│   ├── package.json
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
- **Android Studio** (Ladybug / Hedgehog or newer with Android SDK 35) for mobile development
- A **Google Gemini API Key** (from [Google AI Studio](https://aistudio.google.com/))

---

### Environment Configuration

Create a root `.env` file in the root directory (or configure the environment variables in your deployment):

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
```

For frontend local development, check `frontend/.env.development`:
```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

---

### Running with Docker Compose

To start the database, backend, and frontend together:

```bash
docker-compose up -d
```

- **Application URL**: `http://localhost` (routed through Nginx reverse proxy on port 80)
  - Frontend SPA served at `/`
  - Backend REST API routed at `/api/v1`
  - Real-time WebSockets & STOMP routed at `/ws`

---

### Running Locally for Development

#### 1. Start Database
You can spin up just PostgreSQL via Docker:
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
*Note: Flyway will automatically execute database migrations (`V1` through `V12`) on startup.*

#### 3. Start Frontend
```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173) in your browser.

---

### Running the Android App

1. Open **Android Studio** and select **Open** -> choose the `android/` directory.
2. Wait for Gradle sync to complete.
3. Configure the backend URL in the app's login screen:
   - For an **Android Emulator**: `http://10.0.2.2:8080/`
   - For a **Physical Device**: `http://<your-lan-ip>:8080/` (or your live production backend URL)
4. Select your device or emulator and click **Run** (`Shift + F10`).
5. To execute unit tests:
   ```bash
   cd android
   ./gradlew test
   ```

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

TodoFlow supports dual authentication:
1. **JWT Bearer Authentication**: Short-lived access tokens with automatic refresh token rotation (`/api/v1/auth/login`, `/api/v1/auth/refresh`).
2. **API Key Authentication**: Sent via `X-API-Key: todo_live_...` header for external automation and scripts.

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

- **GitHub Actions Workflows**: Automated linting, test suites, and Docker image builds (`ghcr.io/ajaypds/todo-backend`, `ghcr.io/ajaypds/todo-frontend`).
- **Production Deployment**: Runs on AWS EC2 with Nginx reverse proxy, automatic SSL via Let's Encrypt, and Dynamic DNS.
- **Production Profiles**: Supports cloud databases (such as Supabase PostgreSQL) via `application-prod.yaml` and environment variables.

---

## Modules & License

- **Backend (`backend/`)**: Spring Boot 3.5 application source, JPA entities, event listeners, and Flyway migrations.
- **Android (`android/`)**: Native Kotlin 2.0+ Jetpack Compose mobile app with local-first SQLite database.
- **Frontend (`frontend/`)**: React 19 SPA, Tailwind CSS v4 components, Zustand stores, and TanStack queries.

This project is licensed under the MIT License.
