# TodoFlow MCP Integration Guide

This guide provides step-by-step instructions for integrating **TodoFlow** with major AI assistants—including **Claude Desktop**, **Google Gemini**, **ChatGPT**, and **Cursor**—using the **Model Context Protocol (MCP)** and secure API keys.

---

## Table of Contents
1. [Overview](#1-overview)
2. [Step 1: Generate an API Key in TodoFlow](#2-step-1-generate-an-api-key-in-todoflow)
3. [Step 2: Build the MCP Server](#3-step-2-build-the-mcp-server)
4. [Available MCP Tools](#4-available-mcp-tools)
5. [Claude Desktop Integration](#5-claude-desktop-integration)
6. [Google Gemini Integration](#6-google-gemini-integration)
7. [ChatGPT (Custom GPT Actions) Integration](#7-chatgpt-custom-gpt-actions-integration)
8. [Cursor IDE Integration](#8-cursor-ide-integration)
9. [Prompt Examples & Recipes](#9-prompt-examples--recipes)
10. [Troubleshooting & Security](#10-troubleshooting--security)

---

## 1. Overview

TodoFlow provides an official Model Context Protocol (MCP) server located in the [`mcp-server/`](./mcp-server) directory. 

When connected, AI models can:
- **Inspect** your active projects and task lists.
- **Create** individual or batch tasks with priorities, labels, and due dates.
- **Decompose** complex goals into structured subtask checklists.
- **Complete** or update existing tasks.
- **Sync in Real Time**: Any change performed by an AI immediately reflects in your open browser dashboard via WebSocket push notifications.

---

## 2. Step 1: Generate an API Key in TodoFlow

1. Open your TodoFlow web application (e.g. `http://localhost:5173`).
2. Open the **API Keys & MCP** dialog:
   - Click the **API Keys** icon in the sidebar (or top bar on mobile).
   - Or press `Cmd + K` (Mac) / `Ctrl + K` (Windows) and select **API Keys & MCP Integration**.
3. Click **Create New Key**, give it a descriptive name (e.g., `Claude Desktop` or `Gemini CLI`).
4. **Copy the key immediately** (it starts with `todo_live_...`). 
   > ⚠️ **Note**: Keys are hashed using SHA-256 upon storage. For security, the raw key cannot be displayed again.

---

## 3. Step 2: Build the MCP Server

Ensure your local MCP server bundle is built:

```bash
# Navigate to the mcp-server directory
cd mcp-server

# Install dependencies (only required once)
npm install

# Compile TypeScript to JavaScript (dist/index.js)
npm run build
```

This produces `mcp-server/dist/index.js`.

---

## 4. Available MCP Tools

The TodoFlow MCP server exposes the following 7 tools to your AI agent:

| Tool Name | Description | Key Parameters |
| :--- | :--- | :--- |
| `todoflow_create_task` | Create a single task with priority, project, and due date. | `title` (required), `description`, `priority` (1-4), `dueDate`, `projectName`, `labels` |
| `todoflow_create_tasks_batch` | Create multiple tasks in one round-trip (ideal for checklists and plans). | `tasks`: Array of `{ title, description?, priority?, dueDate?, projectName?, labels? }` |
| `todoflow_list_tasks` | Query and filter tasks from your TodoFlow workspace. | `completed` (boolean), `projectName`, `search` |
| `todoflow_complete_task` | Mark a task as completed or incomplete. | `taskId` (UUID), `completed` (boolean, default: true) |
| `todoflow_add_subtasks` | Add subtask checklist items to an existing task. | `taskId` (UUID), `subtasks`: Array of strings |
| `todoflow_list_projects` | Retrieve all projects with their colors and IDs. | *None* |
| `todoflow_create_project` | Create a new project folder for organizing tasks. | `name` (required), `color` (hex string, e.g. `#6366f1`) |

> 💡 **Automatic Project Resolution**: When specifying `projectName`, the MCP server checks if the project exists. If it does not, it automatically creates it for you!

---

## 5. Claude Desktop Integration

Claude Desktop communicates with the TodoFlow MCP server over standard input/output (`stdio`).

### Configuration

Open your Claude Desktop configuration file:
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

Add the `todoflow` server under `mcpServers`:

```json
{
  "mcpServers": {
    "todoflow": {
      "command": "node",
      "args": [
        "<PATH_TO_TODO_APP>/mcp-server/dist/index.js"
      ],
      "env": {
        "TODOFLOW_API_URL": "http://localhost:8080",
        "TODOFLOW_API_KEY": "todo_live_YOUR_API_KEY_HERE"
      }
    }
  }
}
```
*(Replace `<PATH_TO_TODO_APP>` with the absolute path to your project repository and your API key accordingly)*.

Restart Claude Desktop. You will see a hammer icon indicating that the 7 TodoFlow tools are available.

### Prompt Example for Claude

> **Prompt:**
> *"Look at my current tasks in TodoFlow under the 'Work' project. Then create a new project called 'Sprint 42' with high-priority tasks for API Documentation, Integration Tests, and Release Notes due this Friday."*

---

## 6. Google Gemini Integration

Google Gemini can call the TodoFlow MCP server using the `@google/genai` Node.js SDK, Python SDK (`google-genai`), or via an MCP client runner.

### Python Integration with Gemini

Below is a complete script demonstrating how Google Gemini 1.5/2.0 Flash or Pro connects to TodoFlow:

```python
import os
import requests
from google import genai
from google.genai import types

TODOFLOW_API_KEY = os.getenv("TODOFLOW_API_KEY", "todo_live_YOUR_API_KEY_HERE")
TODOFLOW_API_URL = os.getenv("TODOFLOW_API_URL", "http://localhost:8080/api/v1")

headers = {
    "X-API-Key": TODOFLOW_API_KEY,
    "Content-Type": "application/json"
}

# Define Python tool callable by Gemini
def todoflow_create_task(title: str, description: str = "", priority: int = 2, due_date: str = None) -> str:
    """Create a task in TodoFlow."""
    payload = {
        "title": title,
        "description": description,
        "priority": priority,
    }
    if due_date:
        payload["dueDate"] = due_date
    res = requests.post(f"{TODOFLOW_API_URL}/tasks", json=payload, headers=headers)
    return str(res.json())

def todoflow_list_tasks() -> str:
    """List current tasks from TodoFlow."""
    res = requests.get(f"{TODOFLOW_API_URL}/tasks", headers=headers)
    return str(res.json())

# Initialize Gemini Client
client = genai.Client()

# Query Gemini with automatic tool calling enabled
response = client.models.generate_content(
    model="gemini-2.0-flash",
    contents="Check my TodoFlow tasks and add a high priority task to review quarterly OKRs by Friday.",
    config=types.GenerateContentConfig(
        tools=[todoflow_create_task, todoflow_list_tasks]
    )
)

print(response.text)
```

### Prompt Example for Gemini

> **Prompt:**
> *"I have a technical exam coming up on Cloud Architecture. Break down my study schedule into 4 actionable tasks in TodoFlow with due dates spaced across next week, each tagged with 'ExamPrep' and priority 1."*

---

## 7. ChatGPT (Custom GPT Actions) Integration

ChatGPT can connect directly to your TodoFlow instance via **Custom GPT Actions** or via local MCP-to-OpenAPI bridges.

### Custom GPT Setup (OpenAPI 3.0)

1. Go to **ChatGPT** -> **Explore GPTs** -> **Create a GPT**.
2. Under the **Configure** tab, scroll to **Actions** and click **Create new action**.
3. Under **Authentication**:
   - Authentication Type: `API Key`
   - Auth Type: `Bearer` or `Custom` (Header Name: `X-API-Key`)
   - Paste your `todo_live_...` key.
4. Under **Schema**, paste the OpenAPI 3.0 specification:

```yaml
openapi: 3.0.0
info:
  title: TodoFlow API
  version: 1.0.0
  description: Manage tasks and projects in TodoFlow.
servers:
  - url: https://your-todoflow-domain.com/api/v1
paths:
  /tasks:
    get:
      operationId: getTasks
      summary: Get all tasks
      responses:
        '200':
          description: A list of tasks
    post:
      operationId: createTask
      summary: Create a new task
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [title]
              properties:
                title:
                  type: string
                description:
                  type: string
                priority:
                  type: integer
                  minimum: 1
                  maximum: 4
                dueDate:
                  type: string
                  format: date-time
      responses:
        '201':
          description: Task created
  /tasks/{id}:
    put:
      operationId: updateTask
      summary: Update or complete a task
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                completed:
                  type: boolean
      responses:
        '200':
          description: Task updated
```

### Prompt Example for ChatGPT

> **Prompt:**
> *"Read my meeting notes below and extract all action items. Create each one in TodoFlow with appropriate priorities and deadlines based on what we agreed in the meeting."*

---

## 8. Cursor IDE Integration

If you use **Cursor** or **Windsurf** for AI-assisted coding, you can connect TodoFlow directly to your editor!

### Configuration

Open or create `.cursor/mcp.json` in your project or home directory:

```json
{
  "mcpServers": {
    "todoflow": {
      "command": "node",
      "args": [
        "<PATH_TO_TODO_APP>/mcp-server/dist/index.js"
      ],
      "env": {
        "TODOFLOW_API_URL": "http://localhost:8080",
        "TODOFLOW_API_KEY": "todo_live_YOUR_API_KEY_HERE"
      }
    }
  }
}
```
*(Replace `<PATH_TO_TODO_APP>` with the absolute path to your project repository and your API key accordingly)*.

### Prompt Example for Cursor Composer / Chat

> **Prompt:**
> *"We just refactored the authentication filter. Create a task in TodoFlow called 'Write integration tests for ApiKeyAuthenticationFilter' with subtasks: 'Test valid key', 'Test revoked key', and 'Test malformed header'."*

---

## 9. Prompt Examples & Recipes

### 📋 Recipe 1: Sprint & Feature Planning
```text
I am starting work on an "Export to PDF" feature. Please create a project called "PDF Export" in TodoFlow and create the following tasks inside it:
1. Research PDF generation libraries (Priority 3, due tomorrow)
2. Design report template layout (Priority 2, due in 3 days)
3. Implement backend export endpoint (Priority 1, due in 5 days)
4. Add frontend download button and progress indicator (Priority 2, due in 6 days)
```

### 🎯 Recipe 2: Daily Standup & Task Completion
```text
List all tasks in TodoFlow that are currently incomplete. Summarize what is due today and ask me if I have finished any of them so you can mark them completed.
```

### 📝 Recipe 3: Checklist Breakdown
```text
Find the task titled "Prepare release v2.0" in TodoFlow, and add these subtasks to it:
- Bump version in pom.xml and package.json
- Run test suite and check code coverage
- Draft release notes on GitHub
- Tag git commit v2.0.0
- Deploy backend to production
```

---

## 10. Troubleshooting & Security

### Key Security Best Practices
- **Never commit API keys** to public GitHub or version control repositories.
- TodoFlow only displays the full API key once at creation time. The database only retains the SHA-256 hash.
- You can revoke compromised keys at any time from the **API Keys & MCP Integration** modal in TodoFlow. Revocation takes effect immediately.

### Checking Server Connection
Test your backend connection and key validity directly with `curl`:

```bash
curl -i -H "X-API-Key: todo_live_YOUR_API_KEY_HERE" http://localhost:8080/api/v1/tasks
```

If you receive `HTTP 200 OK`, your key and backend are working properly!

### Common Issues
1. **`HTTP 401 Unauthorized`**: Verify your API key has not been revoked and matches the exact token string (including the `todo_live_` prefix).
2. **Path not found in MCP Server**: Ensure you ran `npm run build` inside `mcp-server/` so `mcp-server/dist/index.js` exists, and provide the absolute path in your client configuration.
