package com.att.tdp.issueflow.workload;

import com.att.tdp.issueflow.workload.dto.WorkloadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Per-project developer workload, per the README ({@code GET /projects/{projectId}/workload}). */
@RestController
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService workloadService;

    @GetMapping("/projects/{projectId}/workload")
    public List<WorkloadResponse> getProjectWorkload(@PathVariable Long projectId) {
        return workloadService.getProjectWorkload(projectId);
    }
}
