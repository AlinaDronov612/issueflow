package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Project endpoints. Paths/methods match the README contract (200 OK on create,
 * PATCH for update). DELETE is a soft delete; listing/restoring soft-deleted
 * projects is ADMIN-only.
 */
@Tag(name = "Projects", description = "Manage projects, soft-delete/restore, and view developer workload")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable Long projectId) {
        return projectService.getProject(projectId);
    }

    @PostMapping
    public ProjectResponse createProject(@Valid @RequestBody CreateProjectRequest request,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return projectService.createProject(request, principal);
    }

    @PatchMapping("/{projectId}")
    public void updateProject(@PathVariable Long projectId,
                              @Valid @RequestBody UpdateProjectRequest request) {
        projectService.updateProject(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    public void deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
    }

    @GetMapping("/deleted")
    public List<ProjectResponse> getDeletedProjects(@AuthenticationPrincipal AuthPrincipal principal) {
        return projectService.getDeletedProjects(principal);
    }

    @PostMapping("/{projectId}/restore")
    public void restoreProject(@PathVariable Long projectId,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        projectService.restoreProject(projectId, principal);
    }
}
