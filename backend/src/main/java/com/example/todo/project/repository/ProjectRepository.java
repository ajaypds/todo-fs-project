package com.example.todo.project.repository;

import com.example.todo.project.entity.Project;
import com.example.todo.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository
        extends JpaRepository<Project, UUID> {

    List<Project> findByUser(User user);
}