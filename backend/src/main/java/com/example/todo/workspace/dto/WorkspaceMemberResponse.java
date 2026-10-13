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
public class WorkspaceMemberResponse {
    private UUID id;
    private UUID userId;
    private String email;
    private String fullName;
    private WorkspaceRole role;
    private Instant joinedAt;
}
