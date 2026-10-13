package com.example.todo.workspace.dto;

import com.example.todo.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRoleRequest {
    @NotNull(message = "Role is required")
    private WorkspaceRole role;
}
