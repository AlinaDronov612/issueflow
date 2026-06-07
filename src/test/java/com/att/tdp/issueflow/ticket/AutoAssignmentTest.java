package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditLog;
import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.common.enums.Actor;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class AutoAssignmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Project project;
    private User admin;
    private User dev1;
    private User dev2;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        auditLogRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        admin = saveUser("adminx", Role.ADMIN);
        dev1 = saveUser("dev1", Role.DEVELOPER);   // registered first
        dev2 = saveUser("dev2", Role.DEVELOPER);   // registered second
        project = projectRepository.save(
                Project.builder().name("P").description("d").owner(admin).build());
    }

    @Test
    void autoAssignsToLeastLoadedDeveloper() throws Exception {
        // dev1 already carries two open tickets; dev2 carries none -> dev2 wins.
        seedTicket(dev1, Status.TODO, false);
        seedTicket(dev1, Status.IN_PROGRESS, false);

        createTicketWithoutAssignee()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(dev2.getId().intValue()));
    }

    @Test
    void tieBreaksByOldestRegistration() throws Exception {
        // Both developers have zero load -> the earlier-registered dev1 wins.
        createTicketWithoutAssignee()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(dev1.getId().intValue()));
    }

    @Test
    void excludesAdminsEvenWhenLessLoaded() throws Exception {
        // Only dev1 exists as a developer and it is loaded; the admin (0 load) must
        // never be chosen. Remove dev2 so dev1 is the sole developer.
        userRepository.delete(dev2);
        seedTicket(dev1, Status.TODO, false);

        createTicketWithoutAssignee()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(dev1.getId().intValue()));
    }

    @Test
    void noDevelopersLeavesAssigneeNullWithoutError() throws Exception {
        userRepository.delete(dev1);
        userRepository.delete(dev2);

        createTicketWithoutAssignee()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    void explicitAssigneeIsRespected() throws Exception {
        // dev2 is least-loaded, but an explicit dev1 must be honored (no auto-assign).
        seedTicket(dev2, Status.TODO, false);
        String body = """
                {"title":"t","priority":"LOW","type":"BUG","projectId":%d,"assigneeId":%d}"""
                .formatted(project.getId(), dev1.getId());

        String response = mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(dev1.getId().intValue()))
                .andReturn().getResponse().getContentAsString();

        Long ticketId = ticketId(response);
        assertThat(autoAssignEntriesFor(ticketId)).isEmpty();
    }

    @Test
    void autoAssignWritesSystemAuditEntry() throws Exception {
        String response = createTicketWithoutAssignee()
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = ticketId(response);

        List<AuditLog> entries = autoAssignEntriesFor(ticketId);
        assertThat(entries).hasSize(1);
        AuditLog entry = entries.get(0);
        assertThat(entry.getActor()).isEqualTo(Actor.SYSTEM);
        assertThat(entry.getPerformedBy()).isNull();
        assertThat(entry.getEntityType()).isEqualTo(AuditEntityType.TICKET);
    }

    private org.springframework.test.web.servlet.ResultActions createTicketWithoutAssignee() throws Exception {
        String body = """
                {"title":"t","priority":"LOW","type":"BUG","projectId":%d}"""
                .formatted(project.getId());
        return mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body));
    }

    private List<AuditLog> autoAssignEntriesFor(Long ticketId) {
        return auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.AUTO_ASSIGN
                        && a.getEntityType() == AuditEntityType.TICKET
                        && a.getEntityId().equals(ticketId))
                .toList();
    }

    private Long ticketId(String json) {
        int i = json.indexOf("\"id\":");
        int start = i + 5;
        int end = json.indexOf(',', start);
        return Long.parseLong(json.substring(start, end).trim());
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
