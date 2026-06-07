package com.att.tdp.issueflow.dependency;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class DependencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketDependencyRepository dependencyRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private Project otherProject;
    private Ticket a;
    private Ticket b;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User owner = userRepository.save(User.builder().username("owner").email("o@e.com")
                .fullName("Owner").role(Role.ADMIN).passwordHash("x").build());
        project = projectRepository.save(Project.builder().name("P1").description("d").owner(owner).build());
        otherProject = projectRepository.save(Project.builder().name("P2").description("d").owner(owner).build());
        a = seedTicket(project, Status.TODO, "A");
        b = seedTicket(project, Status.TODO, "B");
    }

    @Test
    void addDependencyThenListReturnsBlocker() throws Exception {
        addDependency(a.getId(), b.getId()).andExpect(status().isOk());

        mockMvc.perform(get("/tickets/{id}/dependencies", a.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(b.getId().intValue()))
                .andExpect(jsonPath("$[0].title").value("B"))
                .andExpect(jsonPath("$[0].status").value("TODO"));
    }

    @Test
    void addCrossProjectDependencyReturns400() throws Exception {
        Ticket c = seedTicket(otherProject, Status.TODO, "C");

        addDependency(a.getId(), c.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("same project")));
    }

    @Test
    void addDuplicateDependencyReturns409() throws Exception {
        addDependency(a.getId(), b.getId()).andExpect(status().isOk());
        addDependency(a.getId(), b.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already blocked")));
    }

    @Test
    void addSelfDependencyReturns400() throws Exception {
        addDependency(a.getId(), a.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("itself")));
    }

    @Test
    void addDependencyWithUnknownBlockerReturns404() throws Exception {
        addDependency(a.getId(), 99999L).andExpect(status().isNotFound());
    }

    @Test
    void addDependencyOnSoftDeletedTicketReturns404() throws Exception {
        a.setDeleted(true);
        ticketRepository.save(a);

        addDependency(a.getId(), b.getId()).andExpect(status().isNotFound());
    }

    @Test
    void removeDependencyReturns200AndClearsIt() throws Exception {
        addDependency(a.getId(), b.getId()).andExpect(status().isOk());

        mockMvc.perform(delete("/tickets/{id}/dependencies/{blockerId}", a.getId(), b.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/{id}/dependencies", a.getId()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removeUnknownDependencyReturns404() throws Exception {
        mockMvc.perform(delete("/tickets/{id}/dependencies/{blockerId}", a.getId(), b.getId()))
                .andExpect(status().isNotFound());
    }

    // --- the critical rule: no DONE while unresolved blockers ---

    @Test
    void cannotMoveToDoneWhileBlockerUnresolved() throws Exception {
        addDependency(a.getId(), b.getId()).andExpect(status().isOk()); // b is TODO

        mockMvc.perform(patch("/tickets/{id}", a.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"DONE"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("blocking")));

        assertThat(ticketRepository.findById(a.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.TODO); // unchanged
    }

    @Test
    void canMoveToDoneOnceBlockersResolved() throws Exception {
        addDependency(a.getId(), b.getId()).andExpect(status().isOk());

        // Resolve the blocker.
        b.setStatus(Status.DONE);
        ticketRepository.save(b);

        mockMvc.perform(patch("/tickets/{id}", a.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"DONE"}"""))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findById(a.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.DONE);
    }

    private org.springframework.test.web.servlet.ResultActions addDependency(Long ticketId, Long blockerId)
            throws Exception {
        return mockMvc.perform(post("/tickets/{id}/dependencies", ticketId)
                .contentType(APPLICATION_JSON)
                .content("{\"blockedBy\":" + blockerId + "}"));
    }

    private Ticket seedTicket(Project p, Status status, String title) {
        return ticketRepository.save(Ticket.builder()
                .title(title).description("d").status(status).priority(Priority.MEDIUM)
                .type(TicketType.BUG).project(p)
                .overdue(false).priorityManuallySet(false).deleted(false).build());
    }
}
