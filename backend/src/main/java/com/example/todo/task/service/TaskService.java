package com.example.todo.task.service;

import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.label.dto.LabelResponse;
import com.example.todo.label.entity.Label;
import com.example.todo.label.repository.LabelRepository;
import com.example.todo.project.entity.Project;
import com.example.todo.project.repository.ProjectRepository;
import com.example.todo.task.dto.CreateSubtaskRequest;
import com.example.todo.task.dto.CreateTaskRequest;
import com.example.todo.task.dto.ReorderTasksRequest;
import com.example.todo.task.dto.SubtaskResponse;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.dto.UpdateSubtaskRequest;
import com.example.todo.task.dto.UpdateTaskRequest;
import com.example.todo.task.entity.RecurrenceType;
import com.example.todo.task.entity.Subtask;
import com.example.todo.task.entity.Task;
import com.example.todo.task.event.TaskCreatedEvent;
import com.example.todo.task.event.TaskDeletedEvent;
import com.example.todo.task.event.TaskUpdatedEvent;
import com.example.todo.task.repository.SubtaskRepository;
import com.example.todo.task.repository.TaskRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
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

        RecurrenceType recurrenceType = request.getRecurrenceType() != null ? request.getRecurrenceType() : RecurrenceType.NONE;
        Integer recurrenceInterval = request.getRecurrenceInterval() != null && request.getRecurrenceInterval() > 0 ? request.getRecurrenceInterval() : 1;

        Task task = Task.builder()
                .user(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .priority(request.getPriority() != null ? request.getPriority() : 4)
                .dueDate(request.getDueDate())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .project(project)
                .position(position)
                .labels(labels)
                .recurrenceType(recurrenceType)
                .recurrenceInterval(recurrenceInterval)
                .build();

        taskRepository.save(task);

        eventPublisher.publishEvent(new TaskCreatedEvent(task.getId(), task.getTitle(), task.getUser().getId()));

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
        boolean wasNotCompleted = !task.isCompleted();

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

        if (request.getRecurrenceType() != null) {
            task.setRecurrenceType(request.getRecurrenceType());
        }

        if (request.getRecurrenceInterval() != null && request.getRecurrenceInterval() > 0) {
            task.setRecurrenceInterval(request.getRecurrenceInterval());
        }

        if(request.getLabelIds() != null){
            Set<Label> labels = new HashSet<>(labelRepository.findAllById(request.getLabelIds()));
            task.setLabels(labels);
        }

        task.setUpdatedAt(Instant.now());

        taskRepository.save(task);

        eventPublisher.publishEvent(new TaskUpdatedEvent(task.getId(), task.getTitle(), task.getUser().getId()));

        if (Boolean.TRUE.equals(request.getCompleted()) && wasNotCompleted
                && task.getRecurrenceType() != null && task.getRecurrenceType() != RecurrenceType.NONE) {
            spawnNextRecurringInstance(task);
        }

        return mapToResponse(task);
    }

    public void deleteTask(UUID userId, UUID taskId) {

        Task task = getOwnedTask(userId, taskId);

        eventPublisher.publishEvent(new TaskDeletedEvent(taskId, userId));

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

    public SubtaskResponse createSubtask(UUID userId, UUID taskId, CreateSubtaskRequest request) {
        Task task = getOwnedTask(userId, taskId);

        int position = subtaskRepository.countByTaskId(taskId);

        Subtask subtask = Subtask.builder()
                .task(task)
                .title(request.getTitle())
                .completed(false)
                .position(position)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        subtaskRepository.save(subtask);

        eventPublisher.publishEvent(new TaskUpdatedEvent(task.getId(), task.getTitle(), task.getUser().getId()));

        return mapToSubtaskResponse(subtask);
    }

    public SubtaskResponse updateSubtask(UUID userId, UUID taskId, UUID subtaskId, UpdateSubtaskRequest request) {
        Task task = getOwnedTask(userId, taskId);

        Subtask subtask = subtaskRepository.findByIdAndTaskId(subtaskId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        if (request.getTitle() != null) {
            subtask.setTitle(request.getTitle());
        }

        if (request.getCompleted() != null) {
            subtask.setCompleted(request.getCompleted());
        }

        if (request.getPosition() != null) {
            subtask.setPosition(request.getPosition());
        }

        subtask.setUpdatedAt(Instant.now());
        subtaskRepository.save(subtask);

        eventPublisher.publishEvent(new TaskUpdatedEvent(task.getId(), task.getTitle(), task.getUser().getId()));

        return mapToSubtaskResponse(subtask);
    }

    public void deleteSubtask(UUID userId, UUID taskId, UUID subtaskId) {
        Task task = getOwnedTask(userId, taskId);

        Subtask subtask = subtaskRepository.findByIdAndTaskId(subtaskId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        subtaskRepository.delete(subtask);

        eventPublisher.publishEvent(new TaskUpdatedEvent(task.getId(), task.getTitle(), task.getUser().getId()));
    }

    private SubtaskResponse mapToSubtaskResponse(Subtask subtask) {
        return SubtaskResponse.builder()
                .id(subtask.getId())
                .taskId(subtask.getTask().getId())
                .title(subtask.getTitle())
                .completed(subtask.isCompleted())
                .position(subtask.getPosition())
                .createdAt(subtask.getCreatedAt())
                .build();
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
                .recurrenceType(task.getRecurrenceType() != null ? task.getRecurrenceType().name() : "NONE")
                .recurrenceInterval(task.getRecurrenceInterval() != null ? task.getRecurrenceInterval() : 1)
                .labels(task.getLabels().stream()
                        .map(label -> LabelResponse
                                        .builder()
                                        .id(label.getId())
                                        .name(label.getName())
                                        .color(label.getColor())
                                        .build()
                        ).collect(Collectors.toSet()))
                .subtasks(task.getSubtasks() != null ? task.getSubtasks().stream()
                        .map(this::mapToSubtaskResponse)
                        .collect(Collectors.toList()) : Collections.emptyList())
                .build();
    }

    private void spawnNextRecurringInstance(Task task) {
        int position = (int) taskRepository.count();
        ZoneId zoneId;
        try {
            zoneId = task.getUser() != null && task.getUser().getTimezone() != null && !task.getUser().getTimezone().isBlank()
                    ? ZoneId.of(task.getUser().getTimezone())
                    : ZoneOffset.UTC;
        } catch (Exception e) {
            zoneId = ZoneOffset.UTC;
        }

        Instant nextDueDate = calculateNextDueDate(
                task.getDueDate() != null ? task.getDueDate() : Instant.now(),
                task.getRecurrenceType(),
                task.getRecurrenceInterval() != null && task.getRecurrenceInterval() > 0 ? task.getRecurrenceInterval() : 1,
                zoneId
        );

        Task nextTask = Task.builder()
                .user(task.getUser())
                .title(task.getTitle())
                .description(task.getDescription())
                .completed(false)
                .priority(task.getPriority())
                .dueDate(nextDueDate)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .project(task.getProject())
                .position(position)
                .labels(task.getLabels() != null ? new HashSet<>(task.getLabels()) : new HashSet<>())
                .recurrenceType(task.getRecurrenceType())
                .recurrenceInterval(task.getRecurrenceInterval())
                .build();

        Task savedNextTask = taskRepository.save(nextTask);

        if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
            List<Subtask> clonedSubtasks = task.getSubtasks().stream()
                    .map(sub -> Subtask.builder()
                            .task(savedNextTask)
                            .title(sub.getTitle())
                            .completed(false)
                            .position(sub.getPosition())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            subtaskRepository.saveAll(clonedSubtasks);
        }

        eventPublisher.publishEvent(new TaskCreatedEvent(savedNextTask.getId(), savedNextTask.getTitle(), savedNextTask.getUser().getId()));
    }

    private Instant calculateNextDueDate(Instant base, RecurrenceType type, int interval, ZoneId zoneId) {
        if (base == null) {
            base = Instant.now();
        }
        if (interval < 1) {
            interval = 1;
        }
        if (zoneId == null) {
            zoneId = ZoneOffset.UTC;
        }

        ZonedDateTime zdt = base.atZone(zoneId);
        ZonedDateTime nextZdt = switch (type) {
            case DAILY -> zdt.plusDays(interval);
            case WEEKLY -> zdt.plusWeeks(interval);
            case MONTHLY -> zdt.plusMonths(interval);
            case YEARLY -> zdt.plusYears(interval);
            default -> zdt;
        };

        Instant next = nextZdt.toInstant();
        Instant now = Instant.now();
        while (next.isBefore(now)) {
            nextZdt = switch (type) {
                case DAILY -> nextZdt.plusDays(interval);
                case WEEKLY -> nextZdt.plusWeeks(interval);
                case MONTHLY -> nextZdt.plusMonths(interval);
                case YEARLY -> nextZdt.plusYears(interval);
                default -> nextZdt;
            };
            next = nextZdt.toInstant();
        }
        return next;
    }
}