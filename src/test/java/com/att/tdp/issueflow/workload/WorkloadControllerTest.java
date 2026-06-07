package com.att.tdp.issueflow.workload;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class WorkloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private User dev1;
    private User dev2;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User admin = saveUser("adminx", Role.ADMIN);
        dev1 = saveUser("dev1", Role.DEVELOPER);
        dev2 = saveUser("dev2", Role.DEVELOPER);
        project = projectRepository.save(
                Project.builder().name("P").description("d").owner(admin).build());
    }

    @Test
    void workloadReportsOpenCountsPerDeveloperExcludingAdmins() throws Exception {
        // dev1: 2 open; dev2: 1 open. DONE and soft-deleted are excluded from dev1.
        seedTicket(dev1, Status.TODO, false);
        seedTicket(dev1, Status.IN_PROGRESS, false);
        seedTicket(dev1, Status.DONE, false);     // excluded: DONE
        seedTicket(dev1, Status.TODO, true);      // excluded: soft-deleted
        seedTicket(dev2, Status.IN_REVIEW, false);

        // Developers are returned in registration order; admins are not present.
        mockMvc.perform(get("/projects/{id}/workload", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(dev1.getId().intValue()))
                .andExpect(jsonPath("$[0].username").value("dev1"))
                .andExpect(jsonPath("$[0].openTicketCount").value(2))
                .andExpect(jsonPath("$[1].userId").value(dev2.getId().intValue()))
                .andExpect(jsonPath("$[1].openTicketCount").value(1));
    }

    @Test
    void developerWithNoTicketsReportsZero() throws Exception {
        mockMvc.perform(get("/projects/{id}/workload", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].openTicketCount").value(0))
                .andExpect(jsonPath("$[1].openTicketCount").value(0));
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/projects/{id}/workload", 99999))
                .andExpect(status().isNotFound());
    }

    private User saveUser(String username, Role role) {
        return userRepository.save(User.builder()
                .username(username).email(username + "@e.com").fullName(username)
                .role(role).passwordHash("x").build());
    }

    private Ticket seedTicket(User assignee, Status status, boolean deleted) {
        return ticketRepository.save(Ticket.builder()
                .title("seed").description("d").status(status).priority(Priority.LOW)
                .type(TicketType.BUG).project(project).assignee(assignee)
                .overdue(false).priorityManuallySet(false).deleted(deleted).build());
    }
}
