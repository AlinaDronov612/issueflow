package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.auth.AuthPrincipal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketSoftDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private Ticket ticket;
    private RequestPostProcessor asAdmin;
    private RequestPostProcessor asDev;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User admin = userRepository.save(User.builder().username("admin2").email("a2@e.com")
                .fullName("Admin Two").role(Role.ADMIN).passwordHash("x").build());
        User dev = userRepository.save(User.builder().username("dev2").email("d2@e.com")
                .fullName("Dev Two").role(Role.DEVELOPER).passwordHash("x").build());
        project = projectRepository.save(Project.builder().name("P").description("d").owner(admin).build());
        ticket = ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(Status.TODO).priority(Priority.MEDIUM)
                .type(TicketType.BUG).project(project)
                .overdue(false).priorityManuallySet(false).deleted(false).build());
        asAdmin = asUser(admin);
        asDev = asUser(dev);
    }

    @Test
    void softDeleteHidesTicketFromNormalReadsButKeepsRow() throws Exception {
        mockMvc.perform(delete("/tickets/{id}", ticket.getId()).with(asDev))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(asDev))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets").param("projectId", project.getId().toString()).with(asDev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void updatingSoftDeletedTicketReturns404() throws Exception {
        mockMvc.perform(delete("/tickets/{id}", ticket.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .with(asDev).contentType(APPLICATION_JSON).content("""
                                {"title":"x"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedListingIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/tickets/{id}", ticket.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(get("/tickets/deleted").param("projectId", project.getId().toString()).with(asDev))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedListingReturnsRecordsForAdmin() throws Exception {
        mockMvc.perform(delete("/tickets/{id}", ticket.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(get("/tickets/deleted").param("projectId", project.getId().toString()).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ticket.getId().intValue()));
    }

    @Test
    void restoreIsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/tickets/{id}", ticket.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(post("/tickets/{id}/restore", ticket.getId()).with(asDev))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRestoreSoftDeletedTicket() throws Exception {
        mockMvc.perform(delete("/tickets/{id}", ticket.getId()).with(asAdmin)).andExpect(status().isOk());

        mockMvc.perform(post("/tickets/{id}/restore", ticket.getId()).with(asAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/{id}", ticket.getId()).with(asAdmin))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor asUser(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getUsername(), user.getRole()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
