package com.example.todo.activity.repository;

import com.example.todo.activity.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

}