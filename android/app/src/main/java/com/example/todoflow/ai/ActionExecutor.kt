package com.example.todoflow.ai

import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.repository.TaskRepository
import com.example.todoflow.ui.tasks.dueDateFor
import kotlinx.coroutines.flow.first

data class ExecutionResult(val applied: Int, val failures: List<String>)

/**
 * Applies reviewed [Proposal]s through the normal [TaskRepository], so they behave exactly like edits
 * made by hand: shown at once, queued while offline, and sent to the backend API.
 */
class ActionExecutor(private val repository: TaskRepository) {

    suspend fun execute(proposals: List<Proposal>): ExecutionResult {
        val projects = repository.observeProjects().first().toMutableList()
        var applied = 0
        val failures = mutableListOf<String>()

        fun project(name: String): ProjectEntity? = projects.firstOrNull { it.name.equals(name, ignoreCase = true) }

        suspend fun projectIdFor(name: String): ProjectEntity? {
            project(name)?.let { return it }
            val created = repository.createProject(name, DEFAULT_COLOR).getOrNull() ?: return null
            projects += created
            return created
        }

        for (p in proposals) {
            var reason: String? = null
            val ok: Boolean = try {
                when (val a = p.action) {
                    is AssistantAction.CreateTask -> {
                        val target = a.project?.let { projectIdFor(it) }
                        val created = repository.createTask(
                            title = a.title,
                            description = a.description,
                            priority = a.priority ?: 0,
                            dueDate = a.due?.date?.let { dueDateFor(it) },
                            projectId = target?.id,
                            projectName = target?.name
                        ).getOrNull()
                        if (created != null) a.subtasks.forEach { repository.createSubtask(created.id, it) }
                        created != null
                    }
                    is AssistantAction.UpdateTask -> {
                        val task = p.task!!
                        val target = a.project?.name?.let { projectIdFor(it) }
                        val moveProject = a.project != null
                        val result = repository.updateTask(
                            original = task,
                            title = a.title ?: task.title,
                            description = a.description ?: task.description,
                            priority = a.priority ?: task.priority,
                            dueDate = if (a.due != null) a.due.date?.let { dueDateFor(it) } else task.dueDate,
                            projectId = if (moveProject) target?.id else task.projectId,
                            projectName = if (moveProject) target?.name else task.projectName,
                            labels = task.labels
                        )
                        if (a.completed != null && a.completed != task.completed) {
                            repository.toggleTaskCompleted(task.id, a.completed)
                        }
                        result.isSuccess
                    }
                    is AssistantAction.CompleteTask -> repository.toggleTaskCompleted(p.task!!.id, a.completed).isSuccess
                    is AssistantAction.DeleteTask -> repository.deleteTask(p.task!!.id).isSuccess
                    is AssistantAction.AddSubtasks -> {
                        a.titles.forEach { repository.createSubtask(p.task!!.id, it) }
                        true
                    }
                    is AssistantAction.CreateProject -> {
                        val created = repository.createProject(a.name, a.color ?: DEFAULT_COLOR).getOrNull()
                        if (created != null) projects += created
                        created != null
                    }
                    is AssistantAction.UpdateProject -> {
                        val existing = project(a.project)
                        if (existing == null) false
                        else repository.updateProject(existing.id, a.name ?: existing.name, a.color ?: existing.color ?: DEFAULT_COLOR)
                            .getOrNull()?.let { updated ->
                                projects.replaceAll { if (it.id == existing.id) updated else it }
                                true
                            } ?: false
                    }
                    is AssistantAction.DeleteProject -> {
                        val existing = project(a.project)
                        if (existing == null) false
                        else repository.deleteProject(existing.id).isSuccess.also { if (it) projects.remove(existing) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ActionExecutor", "Could not apply: ${p.summary}", e)
                reason = e.message ?: e.javaClass.simpleName
                false
            }
            if (ok) applied++ else failures += p.summary + (reason?.let { " ($it)" } ?: "")
        }
        return ExecutionResult(applied, failures)
    }

    companion object {
        const val DEFAULT_COLOR = "#3b82f6"
    }
}
