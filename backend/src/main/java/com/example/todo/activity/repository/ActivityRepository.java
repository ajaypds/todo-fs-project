package com.example.todo.activity.repository;

import com.example.todo.activity.entity.Activity;
import com.example.todo.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findAllByOrderByCreatedAtDesc();
    List<Activity> findByUserOrderByCreatedAtDesc(User user);
}