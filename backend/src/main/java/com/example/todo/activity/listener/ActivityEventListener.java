package com.example.todo.activity.listener;

import com.example.todo.activity.entity.Activity;
import com.example.todo.activity.repository.ActivityRepository;
import com.example.todo.task.event.TaskCreatedEvent;
import com.example.todo.task.event.TaskDeletedEvent;
import com.example.todo.task.event.TaskUpdatedEvent;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ActivityEventListener {

    private final ActivityRepository repository;
    private final UserRepository userRepository;

    @EventListener
    public void handleCreated(TaskCreatedEvent event) {
        repository.save(Activity.builder()
                .type("TASK_CREATED")
                .message("Created task: " + event.getTitle())
                .createdAt(Instant.now())
                .user(userRepository.findById(event.getUserId()).orElse(null))
                .build()
        );
    }

    @EventListener
    public void handleUpdated(TaskUpdatedEvent event) {
        repository.save(Activity.builder()
                .type("TASK_UPDATED")
                .message("Updated task: " + event.getTitle())
                .createdAt(Instant.now())
                .user(userRepository.findById(event.getUserId()).orElse(null))
                .build()
        );
    }

    @EventListener
    public void handleDeleted(TaskDeletedEvent event) {
        repository.save(Activity.builder()
                .type("TASK_DELETED")
                .message("Deleted a task")
                .createdAt(Instant.now())
                .user(userRepository.findById(event.getUserId()).orElse(null))
                .build()
        );
    }
}