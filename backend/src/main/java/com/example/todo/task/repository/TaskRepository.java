package com.example.todo.task.repository;

import com.example.todo.task.entity.Task;
import com.example.todo.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByUserOrderByPositionAsc(User user, Pageable pageable);
}
