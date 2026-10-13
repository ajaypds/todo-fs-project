package com.example.todo.workspace.repository;

import com.example.todo.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    @Query("SELECT w FROM Workspace w JOIN w.members m WHERE m.user.id = :userId ORDER BY w.createdAt ASC")
    List<Workspace> findAllByUserId(@Param("userId") UUID userId);

    List<Workspace> findByOwnerId(UUID ownerId);
}
