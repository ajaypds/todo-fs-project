package com.example.todo.task.service;

import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.label.dto.LabelResponse;
import com.example.todo.label.entity.Label;
import com.example.todo.label.repository.LabelRepository;
import com.example.todo.project.entity.Project;
import com.example.todo.project.repository.ProjectRepository;
import com.example.todo.task.dto.CreateTaskRequest;
import com.example.todo.task.dto.ReorderTasksRequest;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.dto.UpdateTaskRequest;
import com.example.todo.task.entity.Task;
import com.example.todo.task.event.TaskCreatedEvent;
import com.example.todo.task.event.TaskDeletedEvent;
import com.example.todo.task.event.TaskUpdatedEvent;
import com.example.todo.task.repository.TaskRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final LabelRepository labelRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TaskResponse createTask( UUID userId, CreateTaskRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Project project = null;

        int position = (int) taskRepository.count();

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

        Set<Label> labels = request.getLabelIds() == null ? new HashSet<>() : new HashSet<>(labelRepository.findAllById(request.getLabelIds()));

        Task task = Task.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .priority(request.getPriority() != null ? request.getPriority() : 4)
                .dueDate(request.getDueDate())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .project(project)
                .position(position)
                .labels(labels)
                .build();

        taskRepository.save(task);

        eventPublisher.publishEvent(new TaskCreatedEvent(task.getId(), task.getTitle()));

        return mapToResponse(task);
    }

    public Page<TaskResponse> getTasks(UUID userId, int page, int size) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        return taskRepository.findByUserOrderByPositionAsc(
                user,
                PageRequest.of(page, size)
        ).map(this::mapToResponse);
    }

    public TaskResponse updateTask(UUID userId, UUID taskId, UpdateTaskRequest request) {

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

        if(request.getLabelIds() != null){
            Set<Label> labels = new HashSet<>(labelRepository.findAllById(request.getLabelIds()));
            task.setLabels(labels);
        }

        task.setUpdatedAt(LocalDateTime.now());

        taskRepository.save(task);

        eventPublisher.publishEvent(new TaskUpdatedEvent(task.getId(), task.getTitle()));

        return mapToResponse(task);
    }

    public void deleteTask(UUID userId, UUID taskId) {

        Task task = getOwnedTask(userId, taskId);

        eventPublisher.publishEvent(new TaskDeletedEvent(taskId));

        taskRepository.delete(task);
    }

    private Task getOwnedTask(UUID userId, UUID taskId) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Task not found")
                );

        if (!task.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Task not found");
        }

        return task;
    }

    public void reorderTasks(UUID userId, ReorderTasksRequest request) {

        List<Task> tasks = taskRepository.findAllById(request.getTaskIds());

        for (Task task : tasks) {

            if (!task.getUser()
                    .getId()
                    .equals(userId)) {

                throw new ResourceNotFoundException(
                        "Task not found"
                );
            }
        }

        for (int i = 0; i < request.getTaskIds().size(); i++) {

            UUID taskId = request.getTaskIds().get(i);

            Task task = tasks.stream()
                    .filter(t ->
                            t.getId()
                                    .equals(taskId)
                    )
                    .findFirst()
                    .orElseThrow();

            task.setPosition(i);
        }

        taskRepository.saveAll(tasks);
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
                .position(task.getPosition())
                .labels(task.getLabels().stream()
                        .map(label -> LabelResponse
                                        .builder()
                                        .id(label.getId())
                                        .name(label.getName())
                                        .color(label.getColor())
                                        .build()
                        ).collect(Collectors.toSet()))
                .build();
    }
}