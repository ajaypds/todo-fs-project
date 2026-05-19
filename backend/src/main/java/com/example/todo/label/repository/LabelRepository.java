package com.example.todo.label.repository;

import com.example.todo.label.entity.Label;
import com.example.todo.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {

    List<Label> findByUser(User user);
}