import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import axios, { type AxiosInstance } from "axios";
import { z } from "zod";
import * as dotenv from "dotenv";

dotenv.config();

const BASE_URL = process.env.TODOFLOW_BASE_URL || "http://localhost:8080";
const API_KEY = process.env.TODOFLOW_API_KEY || "";

if (!API_KEY) {
  console.error(
    "Warning: TODOFLOW_API_KEY environment variable is not set. MCP tool calls will require authentication."
  );
}

const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: {
    "Content-Type": "application/json",
    ...(API_KEY ? { "X-API-Key": API_KEY } : {}),
  },
  timeout: 10000,
});

// Helper to resolve project ID from name or create project if it doesn't exist
async function resolveOrCreateProject(projectName?: string, projectId?: string): Promise<string | undefined> {
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

// Create MCP Server
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
      const resolvedProjectId = await resolveOrCreateProject(args.projectName, args.projectId);

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
        const resolvedProjectId = await resolveOrCreateProject(item.projectName);
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

// Start Server with Stdio Transport
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("TodoFlow MCP Server running on stdio transport.");
}

main().catch((err) => {
  console.error("Fatal error starting TodoFlow MCP server:", err);
  process.exit(1);
});
