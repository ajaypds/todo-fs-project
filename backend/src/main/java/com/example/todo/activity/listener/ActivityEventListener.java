package com.example.todo.activity.listener;

import com.example.todo.activity.entity.Activity;
import com.example.todo.activity.repository.ActivityRepository;
import com.example.todo.task.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ActivityEventListener {

    private final ActivityRepository repository;

    @EventListener
    public void handleCreated(TaskCreatedEvent event) {

        repository.save(
                Activity.builder()
                        .type("TASK_CREATED")
                        .message("Created task: " + event.getTitle())
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }
}