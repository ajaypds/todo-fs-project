package com.example.todo.comment.service;

import com.example.todo.activity.entity.Activity;
import com.example.todo.activity.repository.ActivityRepository;
import com.example.todo.comment.dto.CreateTaskCommentRequest;
import com.example.todo.comment.dto.TaskCommentResponse;
import com.example.todo.comment.dto.UpdateTaskCommentRequest;
import com.example.todo.comment.entity.TaskComment;
import com.example.todo.comment.event.TaskCommentCreatedEvent;
import com.example.todo.comment.repository.TaskCommentRepository;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.project.entity.Project;
import com.example.todo.task.entity.Task;
import com.example.todo.task.repository.TaskRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import com.example.todo.workspace.entity.Workspace;
import com.example.todo.workspace.entity.WorkspaceMember;
import com.example.todo.workspace.entity.WorkspaceRole;
import com.example.todo.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskCommentServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskCommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TaskCommentService commentService;

    private User owner;
    private User member;
    private User otherUser;
    private Task task;
    private Workspace workspace;
    private Project project;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .fullName("Owner User")
                .email("owner@example.com")
                .build();

        member = User.builder()
                .id(UUID.randomUUID())
                .fullName("Team Member")
                .email("member@example.com")
                .build();

        otherUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Other User")
                .email("other@example.com")
                .build();

        workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Team Workspace")
                .owner(owner)
                .build();

        project = Project.builder()
                .id(UUID.randomUUID())
                .name("Project Alpha")
                .user(owner)
                .workspace(workspace)
                .build();

        task = Task.builder()
                .id(UUID.randomUUID())
                .title("Complete presentation")
                .user(owner)
                .project(project)
                .build();
    }

    @Test
    void getComments_Success() {
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), member.getId())).thenReturn(true);

        TaskComment comment = TaskComment.builder()
                .id(UUID.randomUUID())
                .task(task)
                .user(owner)
                .content("Initial note")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).thenReturn(List.of(comment));

        List<TaskCommentResponse> result = commentService.getComments(task.getId(), member.getId());

        assertEquals(1, result.size());
        assertEquals("Initial note", result.get(0).getContent());
        assertEquals(owner.getName(), result.get(0).getUserName());
    }

    @Test
    void createComment_Success() {
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), member.getId())).thenReturn(true);
        when(userRepository.findById(member.getId())).thenReturn(Optional.of(member));

        when(commentRepository.save(any(TaskComment.class))).thenAnswer(invocation -> {
            TaskComment c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CreateTaskCommentRequest request = new CreateTaskCommentRequest();
        request.setContent("Looking great! Let's ship it.");

        TaskCommentResponse response = commentService.createComment(task.getId(), request, member.getId());

        assertNotNull(response);
        assertEquals("Looking great! Let's ship it.", response.getContent());
        assertEquals(member.getName(), response.getUserName());
        verify(activityRepository).save(any(Activity.class));
        verify(eventPublisher).publishEvent(any(TaskCommentCreatedEvent.class));
    }

    @Test
    void updateComment_Success_ByAuthor() {
        UUID commentId = UUID.randomUUID();
        TaskComment comment = TaskComment.builder()
                .id(commentId)
                .task(task)
                .user(member)
                .content("Original text")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(TaskComment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskCommentRequest request = new UpdateTaskCommentRequest();
        request.setContent("Updated content");

        TaskCommentResponse response = commentService.updateComment(task.getId(), commentId, request, member.getId());

        assertEquals("Updated content", response.getContent());
    }

    @Test
    void updateComment_ThrowsAccessDenied_WhenNotAuthor() {
        UUID commentId = UUID.randomUUID();
        TaskComment comment = TaskComment.builder()
                .id(commentId)
                .task(task)
                .user(member)
                .content("Original text")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        UpdateTaskCommentRequest request = new UpdateTaskCommentRequest();
        request.setContent("Hacked content");

        assertThrows(AccessDeniedException.class, () ->
                commentService.updateComment(task.getId(), commentId, request, otherUser.getId())
        );
    }

    @Test
    void deleteComment_Success_ByAuthor() {
        UUID commentId = UUID.randomUUID();
        TaskComment comment = TaskComment.builder()
                .id(commentId)
                .task(task)
                .user(member)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deleteComment(task.getId(), commentId, member.getId());

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_Success_ByTaskOwner() {
        UUID commentId = UUID.randomUUID();
        TaskComment comment = TaskComment.builder()
                .id(commentId)
                .task(task)
                .user(member)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deleteComment(task.getId(), commentId, owner.getId());

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_ThrowsAccessDenied_WhenUnauthorized() {
        UUID commentId = UUID.randomUUID();
        TaskComment comment = TaskComment.builder()
                .id(commentId)
                .task(task)
                .user(member)
                .build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), otherUser.getId()))
                .thenReturn(Optional.of(WorkspaceMember.builder().role(WorkspaceRole.MEMBER).build()));

        assertThrows(AccessDeniedException.class, () ->
                commentService.deleteComment(task.getId(), commentId, otherUser.getId())
        );
    }

    @Test
    void getComments_ThrowsNotFound_WhenTaskInaccessible() {
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), otherUser.getId())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                commentService.getComments(task.getId(), otherUser.getId())
        );
    }
}
