package com.att.tdp.issueflow.ticket;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser // ticket endpoints don't read the principal; this satisfies authentication
@Transactional // roll back each test for isolation across the shared context
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private User assignee;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll(); // tickets reference projects/users (FK) -> delete first
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User owner = userRepository.save(User.builder()
                .username("owner").email("owner@example.com").fullName("Owner")
                .role(Role.ADMIN).passwordHash("x").build());
        assignee = userRepository.save(User.builder()
                .username("dev").email("dev@example.com").fullName("Dev")
                .role(Role.DEVELOPER).passwordHash("x").build());
        project = projectRepository.save(Project.builder()
                .name("Proj").description("d").owner(owner).build());
    }

    @Test
    void createTicketReturns200WithAllFields() throws Exception {
        String body = """
                {"title":"Fix login bug","description":"desc","status":"TODO","priority":"HIGH",
                 "type":"BUG","projectId":%d,"assigneeId":%d,"dueDate":"2026-04-01T00:00:00Z"}"""
                .formatted(project.getId(), assignee.getId());

        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Fix login bug"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.type").value("BUG"))
                .andExpect(jsonPath("$.projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$.assigneeId").value(assignee.getId().intValue()))
                .andExpect(jsonPath("$.dueDate").value("2026-04-01T00:00:00Z"))
                .andExpect(jsonPath("$.isOverdue").value(false));
    }

    @Test
    void createDefaultsStatusToTodoWhenOmitted() throws Exception {
        String body = """
                {"title":"t","priority":"LOW","type":"FEATURE","projectId":%d}"""
                .formatted(project.getId());

        // With no assigneeId, the ticket is auto-assigned to the only DEVELOPER (dev).
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.assigneeId").value(assignee.getId().intValue()));
    }

    @Test
    void createWithUnknownProjectReturns404() throws Exception {
        String body = """
                {"title":"t","priority":"LOW","type":"BUG","projectId":99999}""";
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithUnknownAssigneeReturns404() throws Exception {
        String body = """
                {"title":"t","priority":"LOW","type":"BUG","projectId":%d,"assigneeId":99999}"""
                .formatted(project.getId());
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        String body = """
                {"title":"","priority":"LOW","type":"BUG","projectId":%d}""".formatted(project.getId());
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("title")));
    }

    @Test
    void createWithMissingPriorityReturns400() throws Exception {
        String body = """
                {"title":"t","type":"BUG","projectId":%d}""".formatted(project.getId());
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("priority")));
    }

    @Test
    void createWithMissingProjectIdReturns400() throws Exception {
        String body = """
                {"title":"t","priority":"LOW","type":"BUG"}""";
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("projectId")));
    }

    @Test
    void createWithInvalidEnumReturnsInformative400() throws Exception {
        String body = """
                {"title":"t","priority":"URGENT","type":"BUG","projectId":%d}""".formatted(project.getId());
        mockMvc.perform(post("/tickets").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("priority must be one of")));
    }

    @Test
    void getTicketsByProjectReturnsList() throws Exception {
        seedTicket("A");
        seedTicket("B");

        mockMvc.perform(get("/tickets").param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getTicketsByUnknownProjectReturns404() throws Exception {
        mockMvc.perform(get("/tickets").param("projectId", "99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTicketByIdReturns200() throws Exception {
        Ticket ticket = seedTicket("A");

        mockMvc.perform(get("/tickets/{id}", ticket.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.getId().intValue()))
                .andExpect(jsonPath("$.title").value("A"))
                .andExpect(jsonPath("$.projectId").value(project.getId().intValue()));
    }

    @Test
    void getUnknownTicketReturns404() throws Exception {
        mockMvc.perform(get("/tickets/{id}", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTicketFieldsReturns200() throws Exception {
        Ticket ticket = seedTicket("A");

        String body = """
                {"title":"Renamed","description":"new","status":"IN_PROGRESS","priority":"CRITICAL",
                 "assigneeId":%d,"dueDate":"2026-05-01T00:00:00Z"}""".formatted(assignee.getId());

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Renamed");
        assertThat(reloaded.getDescription()).isEqualTo("new");
        assertThat(reloaded.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(reloaded.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(reloaded.getAssignee().getId()).isEqualTo(assignee.getId());
    }

    @Test
    void partialUpdateLeavesOtherFieldsUnchanged() throws Exception {
        Ticket ticket = seedTicket("A");

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"OnlyTitle"}"""))
                .andExpect(status().isOk());

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("OnlyTitle");
        assertThat(reloaded.getPriority()).isEqualTo(Priority.MEDIUM); // seeded value unchanged
        assertThat(reloaded.getType()).isEqualTo(TicketType.BUG);      // type immutable
    }

    @Test
    void updateBlankTitleReturns400() throws Exception {
        Ticket ticket = seedTicket("A");

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("title")));
    }

    @Test
    void updateWithUnknownAssigneeReturns404() throws Exception {
        Ticket ticket = seedTicket("A");

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"assigneeId":99999}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUnknownTicketReturns404() throws Exception {
        mockMvc.perform(patch("/tickets/{id}", 99999)
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"X"}"""))
                .andExpect(status().isNotFound());
    }

    // --- Step 2: forward-only status transitions ---

    @Test
    void forwardStatusTransitionSucceeds() throws Exception {
        Ticket ticket = seedTicket("A", Status.TODO);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"IN_PROGRESS"}"""))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void forwardSkipStatusTransitionSucceeds() throws Exception {
        Ticket ticket = seedTicket("A", Status.TODO);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"IN_REVIEW"}"""))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.IN_REVIEW);
    }

    @Test
    void moveToDoneSucceeds() throws Exception {
        Ticket ticket = seedTicket("A", Status.IN_REVIEW);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"DONE"}"""))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.DONE);
    }

    @Test
    void backwardStatusTransitionReturns409() throws Exception {
        Ticket ticket = seedTicket("A", Status.IN_REVIEW);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"IN_PROGRESS"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("forward")));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(Status.IN_REVIEW); // unchanged
    }

    @Test
    void backwardToTodoReturns409() throws Exception {
        Ticket ticket = seedTicket("A", Status.IN_PROGRESS);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"TODO"}"""))
                .andExpect(status().isConflict());
    }

    // --- Step 2: a DONE ticket cannot be updated at all ---

    @Test
    void updatingNonDoneTicketSucceeds() throws Exception {
        Ticket ticket = seedTicket("A", Status.IN_PROGRESS);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"Edited"}"""))
                .andExpect(status().isOk());

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getTitle())
                .isEqualTo("Edited");
    }

    @Test
    void updatingDoneTicketReturns409() throws Exception {
        Ticket ticket = seedTicket("A", Status.DONE);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"Edited"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("DONE")));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getTitle())
                .isEqualTo("A"); // unchanged
    }

    @Test
    void changingStatusOfDoneTicketReturns409() throws Exception {
        Ticket ticket = seedTicket("A", Status.DONE);

        mockMvc.perform(patch("/tickets/{id}", ticket.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"status":"IN_REVIEW"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/tickets?projectId=1").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private Ticket seedTicket(String title) {
        return seedTicket(title, Status.TODO);
    }

    private Ticket seedTicket(String title, Status status) {
        return ticketRepository.save(Ticket.builder()
                .title(title)
                .description("seed")
                .status(status)
                .priority(Priority.MEDIUM)
                .type(TicketType.BUG)
                .project(project)
                .overdue(false)
                .priorityManuallySet(false)
                .deleted(false)
                .build());
    }
}
