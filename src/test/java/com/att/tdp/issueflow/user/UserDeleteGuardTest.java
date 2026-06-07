package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.audit.AuditLog;
import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.enums.Actor;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Open-Q #5: DELETE /users/{id} is a hard delete, rejected with 409 if the user
 * is still referenced by a ticket (assignee), comment (author), or audit log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class UserDeleteGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User target;
    private Project project;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        User owner = userRepository.save(User.builder().username("owner").email("o@e.com")
                .fullName("Owner").role(Role.ADMIN).passwordHash("x").build());
        target = userRepository.save(User.builder().username("target").email("t@e.com")
                .fullName("Target").role(Role.DEVELOPER).passwordHash("x").build());
        project = projectRepository.save(Project.builder().name("P").description("d").owner(owner).build());
    }

    @Test
    void deletingUnreferencedUserSucceeds() throws Exception {
        mockMvc.perform(delete("/users/{id}", target.getId()))
                .andExpect(status().isOk());
        assertThat(userRepository.existsById(target.getId())).isFalse();
    }

    @Test
    void deletingUserAssignedToTicketReturns409() throws Exception {
        ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(Status.TODO).priority(Priority.LOW)
                .type(TicketType.BUG).project(project).assignee(target)
                .overdue(false).priorityManuallySet(false).deleted(false).build());

        mockMvc.perform(delete("/users/{id}", target.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("tickets")));
        assertThat(userRepository.existsById(target.getId())).isTrue();
    }

    @Test
    void deletingUserWhoAuthoredCommentReturns409() throws Exception {
        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(Status.TODO).priority(Priority.LOW)
                .type(TicketType.BUG).project(project)
                .overdue(false).priorityManuallySet(false).deleted(false).build());
        commentRepository.save(Comment.builder().ticket(ticket).author(target).content("hi").build());

        mockMvc.perform(delete("/users/{id}", target.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("comments")));
    }

    @Test
    void deletingUserReferencedByAuditLogReturns409() throws Exception {
        auditLogRepository.save(AuditLog.builder()
                .action(AuditAction.CREATE).entityType(AuditEntityType.PROJECT)
                .entityId(project.getId()).performedBy(target.getId()).actor(Actor.USER).build());

        mockMvc.perform(delete("/users/{id}", target.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("audit")));
    }
}
