package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.project.dto.ProjectResponse;
import org.springframework.stereotype.Component;

/** Maps {@link Project} entities to response DTOs so entities never leak to the API. */
@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId()
        );
    }
}
