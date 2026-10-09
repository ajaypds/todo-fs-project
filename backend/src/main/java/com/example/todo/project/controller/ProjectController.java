package com.example.todo.project.controller;

import com.example.todo.project.dto.CreateProjectRequest;
import com.example.todo.project.dto.ProjectResponse;

import com.example.todo.project.service.ProjectService;

import com.example.todo.auth.security.CustomUserDetails;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ProjectResponse createProject(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @Valid
            @RequestBody
            CreateProjectRequest request
    ) {
        return projectService.createProject(userDetails.getUserId(), request);
    }

    @GetMapping
    public List<ProjectResponse> getProjects(
            @AuthenticationPrincipal
            CustomUserDetails userDetails
    ) {
        return projectService.getProjects(userDetails.getUserId());
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(
            @AuthenticationPrincipal
            CustomUserDetails userDetails,
            @PathVariable
            UUID projectId
    ) {
        projectService.deleteProject(userDetails.getUserId(), projectId);
    }
}