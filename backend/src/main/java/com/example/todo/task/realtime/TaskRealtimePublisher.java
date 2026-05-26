package com.example.todo.task.realtime;

import com.example.todo.task.event.TaskCreatedEvent;
import com.example.todo.task.event.TaskDeletedEvent;
import com.example.todo.task.event.TaskUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleTaskCreated(TaskCreatedEvent event) {

        TaskRealtimeEvent payload = TaskRealtimeEvent
                                    .builder()
                                    .type("TASK_CREATED")
                                    .taskId(event.getTaskId())
                                    .title(event.getTitle())
                                    .build();

        messagingTemplate.convertAndSend("/topic/tasks", payload);
    }

    @EventListener
    public void handleTaskUpdated(TaskUpdatedEvent event) {

        TaskRealtimeEvent payload = TaskRealtimeEvent
                        .builder()
                        .type("TASK_UPDATED")
                        .taskId(event.getTaskId())
                        .title(event.getTitle())
                        .build();

        messagingTemplate.convertAndSend("/topic/tasks", payload);
    }

    @EventListener
    public void handleTaskDeleted(TaskDeletedEvent event) {

        TaskRealtimeEvent payload = TaskRealtimeEvent
                        .builder()
                        .type("TASK_DELETED")
                        .taskId(event.getTaskId())
                        .build();

        messagingTemplate.convertAndSend("/topic/tasks", payload);
    }
}