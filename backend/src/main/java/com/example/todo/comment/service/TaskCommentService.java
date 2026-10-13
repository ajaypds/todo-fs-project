package com.example.todo.comment.service;

import com.example.todo.activity.dto.ActivityResponse;
import com.example.todo.activity.entity.Activity;
import com.example.todo.activity.repository.ActivityRepository;
import com.example.todo.comment.dto.CreateTaskCommentRequest;
import com.example.todo.comment.dto.TaskCommentResponse;
import com.example.todo.comment.dto.UpdateTaskCommentRequest;
import com.example.todo.comment.entity.TaskComment;
import com.example.todo.comment.event.TaskCommentCreatedEvent;
import com.example.todo.comment.repository.TaskCommentRepository;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.task.entity.Task;
import com.example.todo.task.repository.TaskRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import com.example.todo.workspace.entity.WorkspaceMember;
import com.example.todo.workspace.entity.WorkspaceRole;
import com.example.todo.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getComments(UUID taskId, UUID userId) {
        Task task = getAccessibleTask(taskId, userId);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public TaskCommentResponse createComment(UUID taskId, CreateTaskCommentRequest request, UUID userId) {
        Task task = getAccessibleTask(taskId, userId);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Instant now = Instant.now();
        TaskComment comment = TaskComment.builder()
                .task(task)
                .user(author)
                .content(request.getContent().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();

        TaskComment saved = commentRepository.save(comment);

        // Record audit activity
        Activity activity = Activity.builder()
                .type("TASK_COMMENTED")
                .message((author.getName() != null ? author.getName() : "A team member") + " commented: " + truncateContent(request.getContent()))
                .createdAt(now)
                .user(author)
                .task(task)
                .build();
        activityRepository.save(activity);

        // Publish realtime event
        eventPublisher.publishEvent(new TaskCommentCreatedEvent(
                saved.getId(),
                task.getId(),
                author.getId(),
                author.getName(),
                author.getEmail(),
                saved.getContent(),
                saved.getCreatedAt()
        ));

        return mapToResponse(saved);
    }

    @Transactional
    public TaskCommentResponse updateComment(UUID taskId, UUID commentId, UpdateTaskCommentRequest request, UUID userId) {
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getTask().getId().equals(taskId)) {
            throw new ResourceNotFoundException("Comment not found for this task");
        }

        if (!comment.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own comments");
        }

        comment.setContent(request.getContent().trim());
        comment.setUpdatedAt(Instant.now());

        TaskComment updated = commentRepository.save(comment);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteComment(UUID taskId, UUID commentId, UUID userId) {
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getTask().getId().equals(taskId)) {
            throw new ResourceNotFoundException("Comment not found for this task");
        }

        boolean isAuthor = comment.getUser().getId().equals(userId);
        boolean isTaskOwner = comment.getTask().getUser().getId().equals(userId);
        boolean isWorkspaceAdmin = false;

        if (comment.getTask().getProject() != null && comment.getTask().getProject().getWorkspace() != null) {
            Optional<WorkspaceMember> membership = workspaceMemberRepository.findByWorkspaceIdAndUserId(
                    comment.getTask().getProject().getWorkspace().getId(), userId);
            if (membership.isPresent()) {
                WorkspaceRole role = membership.get().getRole();
                isWorkspaceAdmin = role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
            }
        }

        if (!isAuthor && !isTaskOwner && !isWorkspaceAdmin) {
            throw new AccessDeniedException("You do not have permission to delete this comment");
        }

        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getTaskActivities(UUID taskId, UUID userId) {
        Task task = getAccessibleTask(taskId, userId);
        return activityRepository.findByTaskIdOrderByCreatedAtDesc(task.getId())
                .stream()
                .map(activity -> ActivityResponse.builder()
                        .id(activity.getId())
                        .type(activity.getType())
                        .message(activity.getMessage())
                        .createdAt(activity.getCreatedAt())
                        .build())
                .toList();
    }

    private Task getAccessibleTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        boolean isOwner = task.getUser().getId().equals(userId);
        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);
        boolean isProjectOwner = task.getProject() != null && task.getProject().getUser().getId().equals(userId);
        boolean isWorkspaceMember = false;

        if (task.getProject() != null && task.getProject().getWorkspace() != null) {
            isWorkspaceMember = workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                    task.getProject().getWorkspace().getId(), userId);
        }

        if (!isOwner && !isAssignee && !isProjectOwner && !isWorkspaceMember) {
            throw new ResourceNotFoundException("Task not found");
        }

        return task;
    }

    private TaskCommentResponse mapToResponse(TaskComment comment) {
        return TaskCommentResponse.builder()
                .id(comment.getId())
                .taskId(comment.getTask().getId())
                .userId(comment.getUser().getId())
                .userName(comment.getUser().getName())
                .userEmail(comment.getUser().getEmail())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 60 ? content.substring(0, 57) + "..." : content;
    }
}
