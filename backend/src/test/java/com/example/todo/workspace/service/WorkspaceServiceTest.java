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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private User owner;
    private User memberUser;
    private Workspace workspace;
    private WorkspaceMember ownerMembership;
    private WorkspaceMember regularMembership;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner User")
                .build();

        memberUser = User.builder()
                .id(UUID.randomUUID())
                .email("member@example.com")
                .fullName("Member User")
                .build();

        workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .owner(owner)
                .createdAt(Instant.now())
                .build();

        ownerMembership = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .joinedAt(Instant.now())
                .build();

        regularMembership = WorkspaceMember.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .user(memberUser)
                .role(WorkspaceRole.MEMBER)
                .joinedAt(Instant.now())
                .build();
    }

    @Test
    void createWorkspace_ShouldCreateWorkspaceAndSetOwner() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest();
        request.setName("Marketing Hub");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceResponse response = workspaceService.createWorkspace(owner.getId(), request);

        assertNotNull(response);
        assertEquals("Marketing Hub", response.getName());
        assertEquals(WorkspaceRole.OWNER, response.getCurrentUserRole());
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
    }

    @Test
    void addMember_ShouldAllowAdminOrOwnerToAddMember() {
        AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest();
        request.setEmail("member@example.com");
        request.setRole(WorkspaceRole.ADMIN);

        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), owner.getId()))
                .thenReturn(Optional.of(ownerMembership));
        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(memberUser));
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), memberUser.getId()))
                .thenReturn(false);

        WorkspaceMemberResponse response = workspaceService.addMember(owner.getId(), workspace.getId(), request);

        assertNotNull(response);
        assertEquals(WorkspaceRole.ADMIN, response.getRole());
        verify(workspaceMemberRepository).save(any(WorkspaceMember.class));
    }

    @Test
    void addMember_ShouldThrowUnauthorized_WhenCallerIsRegularMember() {
        AddWorkspaceMemberRequest request = new AddWorkspaceMemberRequest();
        request.setEmail("invitee@example.com");
        request.setRole(WorkspaceRole.VIEWER);

        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), memberUser.getId()))
                .thenReturn(Optional.of(regularMembership));

        assertThrows(UnauthorizedException.class, () ->
                workspaceService.addMember(memberUser.getId(), workspace.getId(), request)
        );
    }

    @Test
    void updateMemberRole_ShouldThrowBadRequest_WhenTryingToModifyOwnerRole() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(WorkspaceRole.ADMIN);

        WorkspaceMember adminMembership = WorkspaceMember.builder()
                .workspace(workspace)
                .user(memberUser)
                .role(WorkspaceRole.ADMIN)
                .build();

        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), memberUser.getId()))
                .thenReturn(Optional.of(adminMembership));
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), owner.getId()))
                .thenReturn(Optional.of(ownerMembership));

        assertThrows(BadRequestException.class, () ->
                workspaceService.updateMemberRole(memberUser.getId(), workspace.getId(), owner.getId(), request)
        );
    }

    @Test
    void deleteWorkspace_ShouldThrowUnauthorized_WhenCallerIsNotOwner() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), memberUser.getId()))
                .thenReturn(Optional.of(regularMembership));

        assertThrows(UnauthorizedException.class, () ->
                workspaceService.deleteWorkspace(memberUser.getId(), workspace.getId())
        );
    }
}
