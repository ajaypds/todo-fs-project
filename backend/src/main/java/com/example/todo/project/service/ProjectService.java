package com.example.todo.project.service;

import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.exception.UnauthorizedException;
import com.example.todo.project.dto.CreateProjectRequest;
import com.example.todo.project.dto.ProjectResponse;
import com.example.todo.project.entity.Project;
import com.example.todo.project.repository.ProjectRepository;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public void deleteProject(UUID userId, UUID projectId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not allowed to delete this project");
        }

        projectRepository.delete(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID userId, UUID projectId, CreateProjectRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not allowed to update this project");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName());
        }
        if (request.getColor() != null && !request.getColor().isBlank()) {
            project.setColor(request.getColor());
        }

        projectRepository.save(project);
        return mapToResponse(project);
    }

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
                .createdAt(Instant.now())
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