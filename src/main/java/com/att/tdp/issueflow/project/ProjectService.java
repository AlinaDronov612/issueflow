package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findByDeletedFalse().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId) {
        return projectMapper.toResponse(findActiveOrThrow(projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getDeletedProjects(AuthPrincipal principal) {
        requireAdmin(principal);
        return projectRepository.findByDeletedTrue().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, AuthPrincipal principal) {
        // Owner is the authenticated caller. The README includes ownerId in the body,
        // so we accept it but reject any value that isn't the caller.
        if (request.ownerId() != null && !request.ownerId().equals(principal.id())) {
            throw new ForbiddenException("ownerId must match the authenticated user");
        }
        User owner = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .owner(owner)
                .build();
        Project saved = projectRepository.save(project);
        auditService.record(AuditAction.CREATE, AuditEntityType.PROJECT, saved.getId());
        return projectMapper.toResponse(saved);
    }

    @Transactional
    public void updateProject(Long projectId, UpdateProjectRequest request) {
        Project project = findActiveOrThrow(projectId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("name must not be blank");
            }
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        projectRepository.save(project);
        auditService.record(AuditAction.UPDATE, AuditEntityType.PROJECT, projectId);
    }

    /** Soft delete: mark deleted, keep the row. Any authenticated user may delete. */
    @Transactional
    public void deleteProject(Long projectId) {
        Project project = findActiveOrThrow(projectId);
        project.setDeleted(true);
        projectRepository.save(project);
        auditService.record(AuditAction.DELETE, AuditEntityType.PROJECT, projectId);
    }

    /** Restore a soft-deleted project (ADMIN only). */
    @Transactional
    public void restoreProject(Long projectId, AuthPrincipal principal) {
        requireAdmin(principal);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!project.isDeleted()) {
            throw new BadRequestException("project " + projectId + " is not deleted");
        }
        project.setDeleted(false);
        projectRepository.save(project);
        auditService.record(AuditAction.RESTORE, AuditEntityType.PROJECT, projectId);
    }

    private Project findActiveOrThrow(Long projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private void requireAdmin(AuthPrincipal principal) {
        if (principal == null || principal.role() != Role.ADMIN) {
            throw new ForbiddenException("This operation requires ADMIN role");
        }
    }
}
