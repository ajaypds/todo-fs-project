package com.example.todo.workspace.service;

import com.example.todo.exception.BadRequestException;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.exception.UnauthorizedException;
import com.example.todo.user.entity.User;
import com.example.todo.user.repository.UserRepository;
import com.example.todo.workspace.dto.*;
import com.example.todo.workspace.entity.Workspace;
import com.example.todo.workspace.entity.WorkspaceMember;
import com.example.todo.workspace.entity.WorkspaceRole;
import com.example.todo.workspace.repository.WorkspaceMemberRepository;
import com.example.todo.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listUserWorkspaces(UUID userId) {
        User user = getUser(userId);
        List<Workspace> workspaces = workspaceRepository.findAllByUserId(user.getId());

        return workspaces.stream().map(w -> {
            WorkspaceMember member = workspaceMemberRepository
                    .findByWorkspaceIdAndUserId(w.getId(), user.getId())
                    .orElse(null);

            WorkspaceRole role = member != null ? member.getRole() : WorkspaceRole.VIEWER;
            long memberCount = workspaceMemberRepository.countByWorkspaceId(w.getId());

            return WorkspaceResponse.builder()
                    .id(w.getId())
                    .name(w.getName())
                    .ownerId(w.getOwner().getId())
                    .ownerName(w.getOwner().getFullName() != null ? w.getOwner().getFullName() : w.getOwner().getEmail())
                    .currentUserRole(role)
                    .memberCount(memberCount)
                    .createdAt(w.getCreatedAt())
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID userId, UUID workspaceId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        Workspace w = member.getWorkspace();
        long memberCount = workspaceMemberRepository.countByWorkspaceId(w.getId());

        return WorkspaceResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .ownerId(w.getOwner().getId())
                .ownerName(w.getOwner().getFullName() != null ? w.getOwner().getFullName() : w.getOwner().getEmail())
                .currentUserRole(member.getRole())
                .memberCount(memberCount)
                .createdAt(w.getCreatedAt())
                .build();
    }

    @Transactional
    public WorkspaceResponse createWorkspace(UUID userId, CreateWorkspaceRequest request) {
        User user = getUser(userId);

        Workspace workspace = Workspace.builder()
                .name(request.getName().trim())
                .owner(user)
                .createdAt(Instant.now())
                .build();

        workspaceRepository.save(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(WorkspaceRole.OWNER)
                .joinedAt(Instant.now())
                .build();

        workspaceMemberRepository.save(member);

        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .ownerId(user.getId())
                .ownerName(user.getFullName() != null ? user.getFullName() : user.getEmail())
                .currentUserRole(WorkspaceRole.OWNER)
                .memberCount(1)
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID userId, UUID workspaceId, UpdateWorkspaceRequest request) {
        WorkspaceMember member = getMembership(workspaceId, userId);

        if (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners or admins can update workspace settings");
        }

        Workspace workspace = member.getWorkspace();
        workspace.setName(request.getName().trim());
        workspaceRepository.save(workspace);

        long memberCount = workspaceMemberRepository.countByWorkspaceId(workspace.getId());

        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .ownerId(workspace.getOwner().getId())
                .ownerName(workspace.getOwner().getFullName() != null ? workspace.getOwner().getFullName() : workspace.getOwner().getEmail())
                .currentUserRole(member.getRole())
                .memberCount(memberCount)
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteWorkspace(UUID userId, UUID workspaceId) {
        WorkspaceMember member = getMembership(workspaceId, userId);

        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new UnauthorizedException("Only the workspace owner can delete the workspace");
        }

        List<Workspace> userWorkspaces = workspaceRepository.findAllByUserId(userId);
        if (userWorkspaces.size() <= 1) {
            throw new BadRequestException("You cannot delete your only workspace");
        }

        workspaceRepository.delete(member.getWorkspace());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(UUID userId, UUID workspaceId) {
        getMembership(workspaceId, userId); // check access
        List<WorkspaceMember> members = workspaceMemberRepository.findAllWithUserByWorkspaceId(workspaceId);

        return members.stream().map(m -> WorkspaceMemberResponse.builder()
                .id(m.getId())
                .userId(m.getUser().getId())
                .email(m.getUser().getEmail())
                .fullName(m.getUser().getFullName())
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .build()
        ).toList();
    }

    @Transactional
    public WorkspaceMemberResponse addMember(UUID userId, UUID workspaceId, AddWorkspaceMemberRequest request) {
        WorkspaceMember requester = getMembership(workspaceId, userId);

        if (requester.getRole() != WorkspaceRole.OWNER && requester.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners or admins can invite new members");
        }

        if (request.getRole() == WorkspaceRole.OWNER) {
            throw new BadRequestException("A workspace can only have one primary owner");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User targetUser = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email '" + request.getEmail() + "' not found"));

        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUser.getId())) {
            throw new BadRequestException("User is already a member of this workspace");
        }

        WorkspaceMember newMember = WorkspaceMember.builder()
                .workspace(requester.getWorkspace())
                .user(targetUser)
                .role(request.getRole())
                .joinedAt(Instant.now())
                .build();

        workspaceMemberRepository.save(newMember);

        return WorkspaceMemberResponse.builder()
                .id(newMember.getId())
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .fullName(targetUser.getFullName())
                .role(newMember.getRole())
                .joinedAt(newMember.getJoinedAt())
                .build();
    }

    @Transactional
    public WorkspaceMemberResponse updateMemberRole(UUID userId, UUID workspaceId, UUID targetUserId, UpdateMemberRoleRequest request) {
        WorkspaceMember requester = getMembership(workspaceId, userId);

        if (requester.getRole() != WorkspaceRole.OWNER && requester.getRole() != WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Only workspace owners or admins can manage roles");
        }

        if (request.getRole() == WorkspaceRole.OWNER) {
            throw new BadRequestException("Cannot assign OWNER role via role update");
        }

        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in workspace"));

        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new BadRequestException("Cannot modify the owner's role");
        }

        if (requester.getRole() == WorkspaceRole.ADMIN && target.getRole() == WorkspaceRole.ADMIN && !userId.equals(targetUserId)) {
            throw new UnauthorizedException("Admins cannot modify other admins' roles");
        }

        target.setRole(request.getRole());
        workspaceMemberRepository.save(target);

        return WorkspaceMemberResponse.builder()
                .id(target.getId())
                .userId(target.getUser().getId())
                .email(target.getUser().getEmail())
                .fullName(target.getUser().getFullName())
                .role(target.getRole())
                .joinedAt(target.getJoinedAt())
                .build();
    }

    @Transactional
    public void removeMember(UUID userId, UUID workspaceId, UUID targetUserId) {
        WorkspaceMember requester = getMembership(workspaceId, userId);
        WorkspaceMember target = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in workspace"));

        if (target.getRole() == WorkspaceRole.OWNER) {
            throw new BadRequestException("Cannot remove the workspace owner");
        }

        boolean isSelf = userId.equals(targetUserId);
        boolean isAdminOrOwner = requester.getRole() == WorkspaceRole.OWNER || requester.getRole() == WorkspaceRole.ADMIN;

        if (!isSelf && !isAdminOrOwner) {
            throw new UnauthorizedException("You do not have permission to remove this member");
        }

        if (!isSelf && requester.getRole() == WorkspaceRole.ADMIN && target.getRole() == WorkspaceRole.ADMIN) {
            throw new UnauthorizedException("Admins cannot remove other admins");
        }

        workspaceMemberRepository.delete(target);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private WorkspaceMember getMembership(UUID workspaceId, UUID userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new UnauthorizedException("You are not a member of this workspace"));
    }
}
