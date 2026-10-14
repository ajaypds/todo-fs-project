import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { randomUUID } from "node:crypto";
import express, { type Request, type Response } from "express";
import cors from "cors";
import axios, { type AxiosInstance } from "axios";
import { z } from "zod";
import * as dotenv from "dotenv";

dotenv.config();

const BASE_URL = process.env.TODOFLOW_BASE_URL || "http://localhost:8080";
const DEFAULT_API_KEY = process.env.TODOFLOW_API_KEY || "";

function createApiClient(token?: string): AxiosInstance {
  const effective = token || DEFAULT_API_KEY;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (effective) {
    if (effective.startsWith("todo_live_")) {
      headers["X-API-Key"] = effective;
    } else {
      headers["Authorization"] = `Bearer ${effective}`;
    }
  }
  return axios.create({
    baseURL: BASE_URL,
    headers,
    timeout: 10000,
  });
}

// Helper to resolve project ID from name or create project if it doesn't exist
async function resolveOrCreateProject(
  apiClient: AxiosInstance,
  projectName?: string,
  projectId?: string
): Promise<string | undefined> {
  if (projectId) return projectId;
  if (!projectName || !projectName.trim()) return undefined;

  try {
    const { data: projects } = await apiClient.get<Array<{ id: string; name: string }>>("/api/v1/projects");
    const match = projects.find(
      (p) => p.name.trim().toLowerCase() === projectName.trim().toLowerCase()
    );
    if (match) return match.id;

    // Create the project if it doesn't exist
    const { data: created } = await apiClient.post<{ id: string; name: string }>("/api/v1/projects", {
      name: projectName.trim(),
      color: "#3b82f6",
    });
    return created.id;
  } catch (error: any) {
    console.error("Error resolving/creating project:", error?.message || error);
    return undefined;
  }
}

/**
 * Factory function to create a new McpServer instance.
 * Multi-session remote SSE transports require a distinct McpServer per transport.
 */
function createMcpServer(apiKey?: string): McpServer {
  const apiClient = createApiClient(apiKey);

  const server = new McpServer({
    name: "todoflow",
    version: "1.0.0",
  });

  // TOOL 1: Create a single task
  server.tool(
    "todoflow_create_task",
    "Create a new task in TodoFlow with optional project, priority, due date, recurrence, and checklist items.",
    {
      title: z.string().describe("Task title (e.g. 'Submit Q3 Tax Filing')"),
      description: z.string().optional().describe("Detailed description or notes in Markdown"),
      priority: z.number().min(1).max(4).optional().describe("Priority level: 1 (Low), 2 (Medium), 3 (High), 4 (Urgent). Default: 1"),
      dueDate: z.string().optional().describe("Due date in ISO format (e.g. '2026-10-15T18:00:00Z' or '2026-10-15')"),
      projectName: z.string().optional().describe("Name of the project. If project doesn't exist, it will be automatically created."),
      projectId: z.string().optional().describe("UUID of an existing project"),
      recurrenceType: z.enum(["NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"]).optional().describe("Recurrence frequency"),
      recurrenceInterval: z.number().min(1).optional().describe("Recurrence interval (e.g. every 2 weeks)"),
      subtasks: z.array(z.string()).optional().describe("Array of subtask checklist titles to create under this task"),
    },
    async (args) => {
      try {
        const resolvedProjectId = await resolveOrCreateProject(apiClient, args.projectName, args.projectId);

        const payload = {
          title: args.title,
          description: args.description,
          priority: args.priority || 1,
          dueDate: args.dueDate || null,
          projectId: resolvedProjectId || null,
          recurrenceType: args.recurrenceType || "NONE",
          recurrenceInterval: args.recurrenceInterval || 1,
        };

        const { data: task } = await apiClient.post<any>("/api/v1/tasks", payload);

        // Add subtasks if requested
        const createdSubtasks: string[] = [];
        if (args.subtasks && args.subtasks.length > 0 && task?.id) {
          for (const subTitle of args.subtasks) {
            try {
              await apiClient.post(`/api/v1/tasks/${task.id}/subtasks`, { title: subTitle });
              createdSubtasks.push(subTitle);
            } catch (e: any) {
              console.error(`Error creating subtask '${subTitle}':`, e?.message);
            }
          }
        }

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  success: true,
                  message: `Task '${task.title}' created successfully in TodoFlow.`,
                  task: {
                    id: task.id,
                    title: task.title,
                    priority: task.priority,
                    dueDate: task.dueDate,
                    projectId: task.projectId,
                    subtasks: createdSubtasks,
                  },
                },
                null,
                2
              ),
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to create task in TodoFlow: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  // TOOL 2: Create batch tasks
  server.tool(
    "todoflow_create_tasks_batch",
    "Create multiple tasks in TodoFlow at once (useful for planning, checklists, project setups).",
    {
      tasks: z.array(
        z.object({
          title: z.string().describe("Task title"),
          description: z.string().optional().describe("Task description in Markdown"),
          priority: z.number().min(1).max(4).optional().describe("1=Low, 2=Medium, 3=High, 4=Urgent"),
          dueDate: z.string().optional().describe("Due date in ISO format or YYYY-MM-DD"),
          projectName: z.string().optional().describe("Project name"),
          subtasks: z.array(z.string()).optional().describe("Checklist subtasks"),
        })
      ).describe("List of tasks to create"),
    },
    async (args) => {
      try {
        const results: any[] = [];
        for (const item of args.tasks) {
          const resolvedProjectId = await resolveOrCreateProject(apiClient, item.projectName);
          const payload = {
            title: item.title,
            description: item.description,
            priority: item.priority || 1,
            dueDate: item.dueDate || null,
            projectId: resolvedProjectId || null,
          };

          const { data: task } = await apiClient.post<any>("/api/v1/tasks", payload);

          if (item.subtasks && item.subtasks.length > 0 && task?.id) {
            for (const subTitle of item.subtasks) {
              await apiClient.post(`/api/v1/tasks/${task.id}/subtasks`, { title: subTitle });
            }
          }

          results.push({
            id: task.id,
            title: task.title,
            subtasksCount: item.subtasks?.length || 0,
          });
        }

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  success: true,
                  message: `Successfully created ${results.length} tasks in TodoFlow.`,
                  tasks: results,
                },
                null,
                2
              ),
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to create batch tasks: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  // TOOL 3: List tasks
  server.tool(
    "todoflow_list_tasks",
    "List tasks from TodoFlow with filters (smart views: inbox, today, upcoming, overdue, or by project name/status).",
    {
      view: z.enum(["all", "inbox", "today", "upcoming", "overdue"]).optional().describe("Smart view filter. Default: 'all'"),
      projectName: z.string().optional().describe("Filter by project name"),
      completed: z.boolean().optional().describe("Filter by completion status"),
      search: z.string().optional().describe("Filter by substring in title or description"),
    },
    async (args) => {
      try {
        const { data } = await apiClient.get<any>("/api/v1/tasks");
        let tasks: any[] = data?.content || [];

        // Filter by project
        if (args.projectName) {
          const { data: projects } = await apiClient.get<Array<{ id: string; name: string }>>("/api/v1/projects");
          const match = projects.find(
            (p) => p.name.trim().toLowerCase() === args.projectName?.trim().toLowerCase()
          );
          if (match) {
            tasks = tasks.filter((t) => t.projectId === match.id);
          }
        }

        // Filter by completion
        if (args.completed !== undefined) {
          tasks = tasks.filter((t) => t.completed === args.completed);
        }

        // Filter by search
        if (args.search && args.search.trim()) {
          const q = args.search.toLowerCase();
          tasks = tasks.filter(
            (t) =>
              t.title?.toLowerCase().includes(q) ||
              t.description?.toLowerCase().includes(q)
          );
        }

        // Filter by smart view
        const now = new Date();
        const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
        const todayEnd = todayStart + 24 * 60 * 60 * 1000;

        if (args.view === "today") {
          tasks = tasks.filter((t) => {
            if (!t.dueDate) return false;
            const due = new Date(t.dueDate).getTime();
            return due < todayEnd;
          });
        } else if (args.view === "upcoming") {
          tasks = tasks.filter((t) => {
            if (!t.dueDate) return false;
            const due = new Date(t.dueDate).getTime();
            return due >= todayEnd;
          });
        } else if (args.view === "overdue") {
          tasks = tasks.filter((t) => {
            if (t.completed || !t.dueDate) return false;
            const due = new Date(t.dueDate).getTime();
            return due < todayStart;
          });
        }

        const formatted = tasks.map((t) => ({
          id: t.id,
          title: t.title,
          completed: t.completed,
          priority: t.priority,
          dueDate: t.dueDate,
          projectId: t.projectId,
          subtasks: t.subtasks?.map((st: any) => ({
            id: st.id,
            title: st.title,
            completed: st.completed,
          })),
        }));

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(
                {
                  count: formatted.length,
                  tasks: formatted,
                },
                null,
                2
              ),
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to list tasks from TodoFlow: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  // TOOL 4: Complete task
  server.tool(
    "todoflow_complete_task",
    "Mark a task as completed (or active) in TodoFlow. Triggers recurrence generation automatically if task is recurring.",
    {
      taskId: z.string().describe("UUID of the task to complete"),
      completed: z.boolean().optional().describe("Target completion state (default: true)"),
    },
    async (args) => {
      try {
        const completed = args.completed !== undefined ? args.completed : true;
        const { data: updated } = await apiClient.patch<any>(`/api/v1/tasks/${args.taskId}`, {
          completed,
        });

        return {
          content: [
            {
              type: "text",
              text: `Task '${updated.title}' is now marked as ${completed ? "completed" : "active"}.`,
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to update task completion: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  // TOOL 5: Add subtasks to an existing task
  server.tool(
    "todoflow_add_subtasks",
    "Add checklist subtasks to an existing task in TodoFlow.",
    {
      taskId: z.string().describe("UUID of the task"),
      subtasks: z.array(z.string()).describe("List of subtask titles to add"),
    },
    async (args) => {
      try {
        const added: string[] = [];
        for (const title of args.subtasks) {
          await apiClient.post(`/api/v1/tasks/${args.taskId}/subtasks`, { title });
          added.push(title);
        }

        return {
          content: [
            {
              type: "text",
              text: `Added ${added.length} subtasks to task checklist.`,
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to add subtasks: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  // TOOL 6: List projects
  server.tool(
    "todoflow_list_projects",
    "List all projects in TodoFlow.",
    {},
    async () => {
      try {
        const { data: projects } = await apiClient.get<Array<{ id: string; name: string; color: string }>>(
          "/api/v1/projects"
        );

        return {
          content: [
            {
              type: "text",
              text: JSON.stringify(projects, null, 2),
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to list projects: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  // TOOL 7: Create project
  server.tool(
    "todoflow_create_project",
    "Create a new project in TodoFlow.",
    {
      name: z.string().describe("Project name"),
      color: z.string().optional().describe("Hex color string (e.g. '#3b82f6'). Default: '#3b82f6'"),
    },
    async (args) => {
      try {
        const { data: project } = await apiClient.post<any>("/api/v1/projects", {
          name: args.name,
          color: args.color || "#3b82f6",
        });

        return {
          content: [
            {
              type: "text",
              text: `Project '${project.name}' created successfully with ID: ${project.id}.`,
            },
          ],
        };
      } catch (error: any) {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: `Failed to create project: ${error?.response?.data?.message || error?.message}`,
            },
          ],
        };
      }
    }
  );

  return server;
}

// Helper to extract API key from headers or query params
function extractApiKey(req: Request): string {
  const authHeader = req.headers["authorization"];
  let reqApiKey =
    (req.headers["x-api-key"] as string) ||
    (req.query.apiKey as string) ||
    (req.query.key as string);

  if (!reqApiKey && authHeader) {
    if (authHeader.startsWith("Bearer ")) {
      reqApiKey = authHeader.substring(7).trim();
    } else {
      reqApiKey = authHeader.trim();
    }
  }

  return reqApiKey || DEFAULT_API_KEY;
}

// Session store for SSE connections
interface SseSessionRecord {
  server: McpServer;
  transport: SSEServerTransport;
  apiKey?: string;
  createdAt: Date;
}

// Session store for Streamable HTTP connections
interface StreamableSessionRecord {
  server: McpServer;
  transport: StreamableHTTPServerTransport;
  apiKey?: string;
  createdAt: Date;
}

const activeSseSessions = new Map<string, SseSessionRecord>();
const activeStreamableSessions = new Map<string, StreamableSessionRecord>();

/**
 * Starts Express HTTP Server supporting both:
 * 1. Server-Sent Events (SSE) on /sse & /message (ChatGPT, Claude Mobile)
 * 2. Unified Streamable HTTP on /mcp (MCP v1.6+ clients)
 */
async function startHttpServer(port: number = 3001) {
  const app = express();

  app.use(cors());
  app.use(express.json());

  // Health check endpoint
  app.get(["/health", "/mcp/health"], (_req: Request, res: Response) => {
    res.json({
      status: "UP",
      activeSessions: {
        sse: activeSseSessions.size,
        streamableHttp: activeStreamableSessions.size,
      },
      transports: ["sse", "streamableHttp", "stdio"],
      timestamp: new Date().toISOString(),
    });
  });

  // Server metadata / discoverability endpoint
  app.get(["/", "/info", "/mcp/info"], (_req: Request, res: Response) => {
    res.json({
      name: "TodoFlow Remote MCP Server",
      version: "1.0.0",
      status: "running",
      transports: {
        sse: {
          handshake: "/sse (or /mcp/sse)",
          messages: "/message (or /mcp/message)",
          description: "Server-Sent Events transport for ChatGPT and Claude Mobile",
        },
        streamableHttp: {
          endpoint: "/mcp (or /streamable)",
          description: "Unified Streamable HTTP transport (POST/GET/DELETE) for MCP v1.6+ clients",
        },
      },
      health: "/health",
    });
  });

  // RFC 8414 OAuth 2.0 Authorization Server Metadata
  app.get(
    [
      "/.well-known/oauth-authorization-server",
      "/mcp/.well-known/oauth-authorization-server",
      "/.well-known/openid-configuration",
    ],
    (req: Request, res: Response) => {
      const proto = req.headers["x-forwarded-proto"] || req.protocol || "https";
      const host = req.headers["host"] || req.get("host") || "todoflow.ddns.net";
      const baseUrl = `${proto}://${host}`;
      res.json({
        issuer: baseUrl,
        authorization_endpoint: `${baseUrl}/oauth2/authorize`,
        token_endpoint: `${baseUrl}/oauth2/token`,
        revocation_endpoint: `${baseUrl}/oauth2/revoke`,
        response_types_supported: ["code"],
        grant_types_supported: ["authorization_code", "refresh_token"],
        code_challenge_methods_supported: ["S256"],
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic", "none"],
        scopes_supported: ["read", "write", "all"],
      });
    }
  );

  // RFC 9728 OAuth 2.0 Protected Resource Metadata
  app.get(
    [
      "/.well-known/oauth-protected-resource",
      "/.well-known/oauth-protected-resource/*",
      "/mcp/.well-known/oauth-protected-resource",
    ],
    (req: Request, res: Response) => {
      const proto = req.headers["x-forwarded-proto"] || req.protocol || "https";
      const host = req.headers["host"] || req.get("host") || "todoflow.ddns.net";
      const baseUrl = `${proto}://${host}`;
      res.json({
        resource: `${baseUrl}/mcp`,
        authorization_servers: [baseUrl],
        scopes_supported: ["read", "write", "all"],
        bearer_methods_supported: ["header"],
        resource_documentation: baseUrl,
      });
    }
  );

  // --------------------------------------------------------------------------
  // TRANSPORT 1: Streamable HTTP (Unified POST / GET / DELETE on /mcp)
  // --------------------------------------------------------------------------
  app.all(["/mcp", "/mcp/streamable", "/streamable"], async (req: Request, res: Response) => {
    // If it's a GET request without session header or without SSE accept header, return info
    if (
      req.method === "GET" &&
      !req.headers["mcp-session-id"] &&
      !req.query.sessionId &&
      !req.headers.accept?.includes("text/event-stream")
    ) {
      res.json({
        name: "TodoFlow Streamable HTTP MCP Endpoint",
        version: "1.0.0",
        endpoint: "/mcp",
        supportedMethods: ["POST", "GET", "DELETE"],
        transport: "streamableHttp",
      });
      return;
    }

    const incomingSessionId = (req.headers["mcp-session-id"] as string) || (req.query.sessionId as string);
    let session = incomingSessionId ? activeStreamableSessions.get(incomingSessionId) : null;

    if (!session) {
      // If client sent an explicit session ID that doesn't exist, return 404
      if (incomingSessionId) {
        res.status(404).json({ error: `Streamable HTTP session '${incomingSessionId}' not found or expired` });
        return;
      }

      // New session initialization (POST)
      const apiKey = extractApiKey(req);
      let newServer: McpServer;

      const transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: () => randomUUID(),
        enableJsonResponse: true,
        onsessioninitialized: (sid: string) => {
          activeStreamableSessions.set(sid, {
            server: newServer,
            transport,
            apiKey,
            createdAt: new Date(),
          });
          console.log(`[MCP-StreamableHTTP] Session initialized: ${sid} (hasApiKey: ${Boolean(apiKey)})`);
        },
        onsessionclosed: (sid: string) => {
          activeStreamableSessions.delete(sid);
          console.log(`[MCP-StreamableHTTP] Session closed: ${sid}`);
        },
      });

      newServer = createMcpServer(apiKey);
      await newServer.connect(transport);
      session = {
        server: newServer,
        transport,
        apiKey,
        createdAt: new Date(),
      };
    }

    try {
      await session.transport.handleRequest(req, res, req.body);
      if (session.transport.sessionId && !activeStreamableSessions.has(session.transport.sessionId)) {
        activeStreamableSessions.set(session.transport.sessionId, session);
      }
    } catch (err: any) {
      console.error(`[MCP-StreamableHTTP] Error handling request:`, err?.message || err);
      if (!res.headersSent) {
        res.status(500).json({ error: err?.message || "Internal error handling Streamable HTTP request" });
      }
    }
  });

  // --------------------------------------------------------------------------
  // TRANSPORT 2: HTTP with Server-Sent Events (SSE on /sse & /message)
  // --------------------------------------------------------------------------
  app.get(["/sse", "/mcp/sse"], async (req: Request, res: Response) => {
    const apiKey = extractApiKey(req);

    // Direct client to the matching message endpoint (supports Nginx reverse proxy prefixes)
    const forwardedPrefix = ((req.headers["x-forwarded-prefix"] as string) || "").replace(/\/$/, "");
    const isMcpPrefixed = Boolean(forwardedPrefix === "/mcp" || req.baseUrl?.startsWith("/mcp") || req.path.startsWith("/mcp"));
    const endpointPath = isMcpPrefixed ? "/mcp/message" : "/message";
    const transport = new SSEServerTransport(endpointPath, res);
    const server = createMcpServer(apiKey);

    const sessionId = transport.sessionId;
    activeSseSessions.set(sessionId, {
      server,
      transport,
      apiKey,
      createdAt: new Date(),
    });

    console.log(`[MCP-SSE] Client connected. SessionId: ${sessionId} (hasApiKey: ${Boolean(apiKey)})`);

    res.on("close", () => {
      console.log(`[MCP-SSE] Session disconnected: ${sessionId}`);
      activeSseSessions.delete(sessionId);
    });

    try {
      await server.connect(transport);
    } catch (err: any) {
      console.error(`[MCP-SSE] Error connecting session ${sessionId}:`, err?.message || err);
      activeSseSessions.delete(sessionId);
    }
  });

  app.post(["/message", "/mcp/message"], async (req: Request, res: Response) => {
    const sessionId = req.query.sessionId as string;
    if (!sessionId) {
      res.status(400).json({ error: "Missing sessionId query parameter" });
      return;
    }

    const session = activeSseSessions.get(sessionId);
    if (!session) {
      res.status(404).json({ error: `Session '${sessionId}' not found or expired` });
      return;
    }

    try {
      await session.transport.handlePostMessage(req, res, req.body);
    } catch (err: any) {
      console.error(`[MCP-SSE] Error handling message for session ${sessionId}:`, err?.message || err);
      if (!res.headersSent) {
        res.status(500).json({ error: err?.message || "Internal error handling message" });
      }
    }
  });

  app.listen(port, () => {
    console.log(`TodoFlow Remote MCP Server is listening on port ${port}`);
    console.log(`  - Health Check:     http://localhost:${port}/health`);
    console.log(`  - SSE Transport:    http://localhost:${port}/sse (or /mcp/sse)`);
    console.log(`  - Streamable HTTP:  http://localhost:${port}/mcp (or /streamable)`);
    console.log(`  - Backend URL:      ${BASE_URL}`);
  });
}

/**
 * Starts standard Stdio Server.
 * Used for local desktop clients (Claude Desktop, Cursor IDE).
 */
async function startStdioServer() {
  const server = createMcpServer(DEFAULT_API_KEY);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("TodoFlow MCP Server running on stdio transport.");
}

async function main() {
  const isHttpMode =
    process.env.MCP_TRANSPORT === "sse" ||
    process.env.MCP_TRANSPORT === "http" ||
    process.env.MCP_TRANSPORT === "streamableHttp" ||
    process.env.MCP_MODE === "sse" ||
    process.env.MCP_MODE === "http" ||
    Boolean(process.env.PORT) ||
    process.argv.includes("--sse") ||
    process.argv.includes("--http");

  if (isHttpMode) {
    const port = parseInt(process.env.PORT || "3001", 10);
    await startHttpServer(port);
  } else {
    await startStdioServer();
  }
}

main().catch((err) => {
  console.error("Fatal error starting TodoFlow MCP server:", err);
  process.exit(1);
});
