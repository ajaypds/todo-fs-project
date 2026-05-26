package com.example.todo.activity.realtime;

import com.example.todo.task.event.TaskCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleTaskCreated(TaskCreatedEvent event) {

        ActivityRealtimeEvent payload = ActivityRealtimeEvent
                                        .builder()
                                        .type("ACTIVITY_CREATED")
                                        .message("Created task: " + event.getTitle())
                                        .build();

        messagingTemplate.convertAndSend("/topic/activities", payload);
    }
}