package com.example.todo.comment.realtime;

import com.example.todo.activity.realtime.ActivityRealtimeEvent;
import com.example.todo.comment.dto.TaskCommentResponse;
import com.example.todo.comment.event.TaskCommentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskCommentRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleCommentCreated(TaskCommentCreatedEvent event) {
        TaskCommentResponse payload = TaskCommentResponse.builder()
                .id(event.getCommentId())
                .taskId(event.getTaskId())
                .userId(event.getAuthorId())
                .userName(event.getAuthorName())
                .userEmail(event.getAuthorEmail())
                .content(event.getContent())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getCreatedAt())
                .build();

        messagingTemplate.convertAndSend("/topic/tasks/" + event.getTaskId() + "/comments", payload);

        ActivityRealtimeEvent activityPayload = ActivityRealtimeEvent.builder()
                .type("TASK_COMMENTED")
                .message((event.getAuthorName() != null ? event.getAuthorName() : "A user") + " commented on a task")
                .build();
        messagingTemplate.convertAndSend("/topic/users/" + event.getAuthorId() + "/activities", activityPayload);
    }
}
