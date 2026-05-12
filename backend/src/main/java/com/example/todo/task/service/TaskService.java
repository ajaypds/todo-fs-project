package com.example.todo.task.service;

import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.project.entity.Project;
import com.example.todo.project.repository.ProjectRepository;
import com.example.todo.task.dto.CreateTaskRequest;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.dto.UpdateTaskRequest;
import com.example.todo.task.entity.Task;
import com.example.todo.task.repository.TaskRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public TaskResponse createTask(
            UUID userId,
            CreateTaskRequest request
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Project project = null;

        if (request.getProjectId() != null) {

            project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() ->
                    new ResourceNotFoundException(
                            "Project not found"
                    )
            );

            if (!project.getUser().getId()
                    .equals(userId)) {

                throw new ResourceNotFoundException(
                        "Project not found"
                );
            }
        }

        Task task = Task.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .priority(
                        request.getPriority() != null
                                ? request.getPriority()
                                : 4
                )
                .dueDate(request.getDueDate())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .project(project)
                .build();

        taskRepository.save(task);

        return mapToResponse(task);
    }

    public Page<TaskResponse> getTasks(
            UUID userId,
            int page,
            int size
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        return taskRepository.findByUser(
                user,
                PageRequest.of(page, size)
        ).map(this::mapToResponse);
    }

    public TaskResponse updateTask(
            UUID userId,
            UUID taskId,
            UpdateTaskRequest request
    ) {

        Task task = getOwnedTask(userId, taskId);

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }

        if (request.getCompleted() != null) {
            task.setCompleted(request.getCompleted());
        }

        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }

        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }

        task.setUpdatedAt(LocalDateTime.now());

        taskRepository.save(task);

        return mapToResponse(task);
    }

    public void deleteTask(
            UUID userId,
            UUID taskId
    ) {

        Task task = getOwnedTask(userId, taskId);

        taskRepository.delete(task);
    }

    private Task getOwnedTask(
            UUID userId,
            UUID taskId
    ) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Task not found")
                );

        if (!task.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Task not found");
        }

        return task;
    }

    private TaskResponse mapToResponse(Task task) {

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .completed(task.isCompleted())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .build();
    }
}