package com.att.tdp.issueflow.workload;

import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.workload.dto.WorkloadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Computes developer workload within a project. Workload = a developer's
 * non-DONE, non-soft-deleted tickets assigned to them in that project. Used both
 * by the workload endpoint and by ticket auto-assignment.
 */
@Service
@RequiredArgsConstructor
public class WorkloadService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;

    /** Per-developer open-ticket counts for a project (ADMINs excluded). */
    @Transactional(readOnly = true)
    public List<WorkloadResponse> getProjectWorkload(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return developersByRegistration().stream()
                .map(dev -> new WorkloadResponse(
                        dev.getId(), dev.getUsername(), openTicketCount(dev.getId(), projectId)))
                .toList();
    }

    /**
     * The least-loaded DEVELOPER for a project, or empty if none exist. Ties are
     * broken by oldest registration first (the candidate list is already ordered,
     * and strict less-than keeps the earliest registrant on a tie).
     */
    @Transactional(readOnly = true)
    public Optional<User> selectLeastLoadedDeveloper(Project project) {
        User best = null;
        long bestLoad = Long.MAX_VALUE;
        for (User dev : developersByRegistration()) {
            long load = openTicketCount(dev.getId(), project.getId());
            if (load < bestLoad) {
                bestLoad = load;
                best = dev;
            }
        }
        return Optional.ofNullable(best);
    }

    private List<User> developersByRegistration() {
        return userRepository.findByRoleOrderByCreatedAtAscIdAsc(Role.DEVELOPER);
    }

    private long openTicketCount(Long developerId, Long projectId) {
        return ticketRepository.countByAssigneeIdAndProjectIdAndDeletedFalseAndStatusNot(
                developerId, projectId, Status.DONE);
    }
}
