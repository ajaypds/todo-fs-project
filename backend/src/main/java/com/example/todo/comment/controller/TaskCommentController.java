package com.example.todo.comment.controller;

import com.example.todo.activity.dto.ActivityResponse;
import com.example.todo.auth.security.CustomUserDetails;
import com.example.todo.comment.dto.CreateTaskCommentRequest;
import com.example.todo.comment.dto.TaskCommentResponse;
import com.example.todo.comment.dto.UpdateTaskCommentRequest;
import com.example.todo.comment.service.TaskCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}")
@RequiredArgsConstructor
public class TaskCommentController {

    private final TaskCommentService commentService;

    @GetMapping("/comments")
    public List<TaskCommentResponse> getComments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID taskId
    ) {
        return commentService.getComments(taskId, userDetails.getUserId());
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskCommentResponse createComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskCommentRequest request
    ) {
        return commentService.createComment(taskId, request, userDetails.getUserId());
    }

    @PutMapping("/comments/{commentId}")
    public TaskCommentResponse updateComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateTaskCommentRequest request
    ) {
        return commentService.updateComment(taskId, commentId, request, userDetails.getUserId());
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID taskId,
            @PathVariable UUID commentId
    ) {
        commentService.deleteComment(taskId, commentId, userDetails.getUserId());
    }

    @GetMapping("/activities")
    public List<ActivityResponse> getTaskActivities(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID taskId
    ) {
        return commentService.getTaskActivities(taskId, userDetails.getUserId());
    }
}
