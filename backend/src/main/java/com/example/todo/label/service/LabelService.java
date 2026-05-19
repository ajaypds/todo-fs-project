package com.example.todo.label.service;

import com.example.todo.label.dto.*;
import com.example.todo.label.entity.Label;
import com.example.todo.label.repository.LabelRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;

    private final UserRepository userRepository;

    public LabelResponse createLabel(UUID userId, CreateLabelRequest request) {

        User user = userRepository.findById(userId).orElseThrow();

        Label label = labelRepository.save(
                        Label.builder()
                            .name(request.getName())
                            .color(request.getColor())
                            .user(user)
                            .build()
                );

        return map(label);
    }

    public List<LabelResponse> getLabels(UUID userId) {

        User user = userRepository.findById(userId).orElseThrow();

        return labelRepository
                .findByUser(user)
                .stream()
                .map(this::map)
                .toList();
    }

    private LabelResponse map(Label label) {

        return LabelResponse
                .builder()
                .id(label.getId())
                .name(label.getName())
                .color(label.getColor())
                .build();
    }
}