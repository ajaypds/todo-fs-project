package com.example.todo.task.service;

import com.example.todo.label.repository.LabelRepository;
import com.example.todo.project.repository.ProjectRepository;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.dto.UpdateTaskRequest;
import com.example.todo.task.entity.Task;
import com.example.todo.task.repository.SubtaskRepository;
import com.example.todo.task.repository.TaskRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private SubtaskRepository subtaskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private LabelRepository labelRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TaskService taskService;

    private final UUID userId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();
    private final Instant due = Instant.parse("2026-11-20T23:59:59Z");
    private Task task;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(userId);
        task = Task.builder()
                .id(taskId)
                .title("Pay rent")
                .user(owner)
                .dueDate(due)
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
    }

    @Test
    void updateWithoutDueDateFieldsKeepsExistingDueDate() {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("Pay rent (renamed)");

        TaskResponse response = taskService.updateTask(userId, taskId, request);

        assertEquals(due, response.getDueDate());
        assertEquals("Pay rent (renamed)", response.getTitle());
    }

    @Test
    void updateWithNewDueDateReplacesIt() {
        UpdateTaskRequest request = new UpdateTaskRequest();
        Instant newDue = Instant.parse("2026-12-01T23:59:59Z");
        request.setDueDate(newDue);

        TaskResponse response = taskService.updateTask(userId, taskId, request);

        assertEquals(newDue, response.getDueDate());
    }

    @Test
    void clearDueDateRemovesTheDueDate() {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setClearDueDate(true);

        TaskResponse response = taskService.updateTask(userId, taskId, request);

        assertNull(response.getDueDate());
    }

    @Test
    void clearDueDateWinsOverADueDateInTheSameRequest() {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setClearDueDate(true);
        request.setDueDate(Instant.parse("2026-12-01T23:59:59Z"));

        TaskResponse response = taskService.updateTask(userId, taskId, request);

        assertNull(response.getDueDate());
    }

    @Test
    void clearDueDateFalseDoesNothing() {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setClearDueDate(false);

        TaskResponse response = taskService.updateTask(userId, taskId, request);

        assertEquals(due, response.getDueDate());
    }
}
