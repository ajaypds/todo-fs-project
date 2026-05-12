package com.example.todo.project.service;

import com.example.todo.exception.ResourceNotFoundException;

import com.example.todo.project.dto.CreateProjectRequest;
import com.example.todo.project.dto.ProjectResponse;

import com.example.todo.project.entity.Project;
import com.example.todo.project.repository.ProjectRepository;

import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    private final UserRepository userRepository;

    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );

        Project project = Project.builder()
                .user(user)
                .name(request.getName())
                .color(request.getColor())
                .createdAt(LocalDateTime.now())
                .build();

        projectRepository.save(project);

        return mapToResponse(project);
    }

    public List<ProjectResponse> getProjects(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );

        return projectRepository.findByUser(user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ProjectResponse mapToResponse(Project project) {

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .color(project.getColor())
                .build();
    }
}