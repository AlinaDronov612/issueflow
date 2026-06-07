package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditLog;
import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.auth.AuthPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class AutoEscalationTest {

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
    private RequestPostProcessor asAdmin;
    private RequestPostProcessor asDev;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        auditLogRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User admin = saveUser("adminx", Role.ADMIN);
        User dev = saveUser("dev1", Role.DEVELOPER);
        project = projectRepository.save(
                Project.builder().name("P").description("d").owner(admin).build());
        asAdmin = asUser(admin);
        asDev = asUser(dev);
    }

    @Test
    void overdueNonCriticalTicketEscalatesOneLevel() throws Exception {
        Ticket ticket = seed(Priority.LOW, Status.TODO, pastDue(), false, false);

        escalate().andExpect(status().isOk()).andExpect(jsonPath("$.escalated").value(1));

        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(after.getStatus()).isEqualTo(Status.TODO); // status never changes
        assertThat(after.isOverdue()).isFalse();
    }

    @Test
    void criticalTicketSetsOverdueAndDoesNotEscalateFurther() throws Exception {
        Ticket ticket = seed(Priority.CRITICAL, Status.IN_PROGRESS, pastDue(), false, false);

        escalate().andExpect(jsonPath("$.escalated").value(0)); // flagging overdue is not an escalation
        Ticket after = reload(ticket);
        assertThat(after.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(after.isOverdue()).isTrue();

        // Idempotent: a second run changes nothing.
        escalate().andExpect(jsonPath("$.escalated").value(0));
        Ticket again = reload(ticket);
        assertThat(again.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(again.isOverdue()).isTrue();
    }

    @Test
    void nonOverdueTicketsAreUntouched() throws Exception {
        Ticket future = seed(Priority.LOW, Status.TODO, futureDue(), false, false);
        Ticket noDueDate = seed(Priority.LOW, Status.TODO, null, false, false);

        escalate().andExpect(jsonPath("$.escalated").value(0));

        assertThat(reload(future).getPriority()).isEqualTo(Priority.LOW);
        assertThat(reload(noDueDate).getPriority()).isEqualTo(Priority.LOW);
    }

    @Test
    void doneTicketIsUntouched() throws Exception {
        Ticket done = seed(Priority.HIGH, Status.DONE, pastDue(), false, false);

        escalate().andExpect(jsonPath("$.escalated").value(0));

        assertThat(reload(done).getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void manualPriorityChangeResetsEscalationStateAndIsNotImmediatelyReEscalated() throws Exception {
        Ticket ticket = seed(Priority.LOW, Status.TODO, pastDue(), false, true /* overdue */);

        // Manual PATCH resets escalation state: overdue cleared, priorityManuallySet set.
        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"priority":"HIGH"}"""))
                .andExpect(status().isOk());
        Ticket afterPatch = reload(ticket);
        assertThat(afterPatch.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(afterPatch.isPriorityManuallySet()).isTrue();
        assertThat(afterPatch.isOverdue()).isFalse();

        // First sweep does not re-escalate (grace cycle consumes the manual flag).
        escalate().andExpect(jsonPath("$.escalated").value(0));
        Ticket afterFirst = reload(ticket);
        assertThat(afterFirst.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(afterFirst.isPriorityManuallySet()).isFalse();

        // A later sweep resumes escalation normally.
        escalate().andExpect(jsonPath("$.escalated").value(1));
        assertThat(reload(ticket).getPriority()).isEqualTo(Priority.CRITICAL);
    }

    @Test
    void escalationWritesSystemAuditEntry() throws Exception {
        Ticket ticket = seed(Priority.MEDIUM, Status.TODO, pastDue(), false, false);

        escalate().andExpect(jsonPath("$.escalated").value(1));

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.AUTO_ESCALATE
                        && a.getEntityType() == AuditEntityType.TICKET
                        && a.getEntityId().equals(ticket.getId()))
                .toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getActor()).isEqualTo(Actor.SYSTEM);
        assertThat(entries.get(0).getPerformedBy()).isNull();
    }

    @Test
    void escalateEndpointRequiresAdmin() throws Exception {
        mockMvc.perform(post("/tickets/escalate").with(asDev))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions escalate() throws Exception {
        return mockMvc.perform(post("/tickets/escalate").with(asAdmin));
    }

    private Ticket reload(Ticket ticket) {
        return ticketRepository.findById(ticket.getId()).orElseThrow();
    }

    private Instant pastDue() {
        return Instant.now().minusSeconds(3600);
    }

    private Instant futureDue() {
        return Instant.now().plusSeconds(3600);
    }

    private User saveUser(String username, Role role) {
        return userRepository.save(User.builder()
                .username(username).email(username + "@e.com").fullName(username)
                .role(role).passwordHash("x").build());
    }

    private Ticket seed(Priority priority, Status status, Instant dueDate,
                        boolean manuallySet, boolean overdue) {
        return ticketRepository.save(Ticket.builder()
                .title("seed").description("d").status(status).priority(priority)
                .type(TicketType.BUG).project(project).dueDate(dueDate)
                .overdue(overdue).priorityManuallySet(manuallySet).deleted(false).build());
    }

    private RequestPostProcessor asUser(User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
