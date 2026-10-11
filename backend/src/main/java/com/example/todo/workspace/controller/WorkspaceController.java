package com.example.todo.workspace.controller;

import com.example.todo.auth.security.CustomUserDetails;
import com.example.todo.workspace.dto.*;
import com.example.todo.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public List<WorkspaceResponse> listWorkspaces(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return workspaceService.listUserWorkspaces(userDetails.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse createWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return workspaceService.createWorkspace(userDetails.getUserId(), request);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId
    ) {
        return workspaceService.getWorkspace(userDetails.getUserId(), workspaceId);
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceResponse updateWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request
    ) {
        return workspaceService.updateWorkspace(userDetails.getUserId(), workspaceId, request);
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId
    ) {
        workspaceService.deleteWorkspace(userDetails.getUserId(), workspaceId);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> listMembers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId
    ) {
        return workspaceService.listMembers(userDetails.getUserId(), workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse addMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request
    ) {
        return workspaceService.addMember(userDetails.getUserId(), workspaceId, request);
    }

    @PatchMapping("/{workspaceId}/members/{targetUserId}")
    public WorkspaceMemberResponse updateMemberRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return workspaceService.updateMemberRole(userDetails.getUserId(), workspaceId, targetUserId, request);
    }

    @DeleteMapping("/{workspaceId}/members/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID workspaceId,
            @PathVariable UUID targetUserId
    ) {
        workspaceService.removeMember(userDetails.getUserId(), workspaceId, targetUserId);
    }
}
