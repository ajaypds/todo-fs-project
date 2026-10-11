package com.example.todo.workspace.dto;

import com.example.todo.workspace.entity.WorkspaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceResponse {
    private UUID id;
    private String name;
    private UUID ownerId;
    private String ownerName;
    private WorkspaceRole currentUserRole;
    private long memberCount;
    private Instant createdAt;
}
