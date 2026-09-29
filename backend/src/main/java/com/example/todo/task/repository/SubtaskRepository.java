package com.example.todo.task.repository;

import com.example.todo.task.entity.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

    List<Subtask> findByTaskIdOrderByPositionAscCreatedAtAsc(UUID taskId);

    Optional<Subtask> findByIdAndTaskId(UUID id, UUID taskId);

    int countByTaskId(UUID taskId);
}
