package com.example.todo.activity.service;

import com.example.todo.activity.dto.ActivityResponse;
import com.example.todo.activity.repository.ActivityRepository;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public List<ActivityResponse> getActivities(UUID userId) {

        User user = userRepository.findById(userId).orElseThrow(()->
            new ResourceNotFoundException("User not found")
        );

        return activityRepository.findByUserOrderByCreatedAtDesc(user)
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