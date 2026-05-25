package com.example.todo.activity.service;

import com.example.todo.activity.dto.ActivityResponse;
import com.example.todo.activity.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository repository;

    public List<ActivityResponse> getActivities() {

        return repository.findAll()
                .stream()
                .map(activity -> ActivityResponse
                                .builder()
                                .id(activity.getId())
                                .type(activity.getType())
                                .message(activity.getMessage())
                                .createdAt(activity.getCreatedAt())
                                .build()
                )
                .toList();
    }
}