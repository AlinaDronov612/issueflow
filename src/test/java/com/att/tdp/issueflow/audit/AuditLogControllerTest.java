package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
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
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    private User actor;
    private Project project;
    private RequestPostProcessor asActor;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        actor = userRepository.save(User.builder()
                .username("actor").email("actor@example.com").fullName("Actor")
                .role(Role.ADMIN).passwordHash("x").build());
        project = projectRepository.save(Project.builder()
                .name("Proj").description("d").owner(actor).build());
        asActor = authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(actor.getId(), actor.getUsername(), Role.ADMIN),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void createUserWritesCreateEntryWithPerformedByFromPrincipal() throws Exception {
        long newUserId = createUser("newbie");

        mockMvc.perform(get("/audit-logs")
                        .param("entityType", "USER").param("action", "CREATE").with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.entityId == " + newUserId + ")].action").value(hasItem("CREATE")))
                .andExpect(jsonPath("$[?(@.entityId == " + newUserId + ")].entityType").value(hasItem("USER")))
                .andExpect(jsonPath("$[?(@.entityId == " + newUserId + ")].actor").value(hasItem("USER")))
                .andExpect(jsonPath("$[?(@.entityId == " + newUserId + ")].performedBy")
                        .value(hasItem(actor.getId().intValue())));
    }

    @Test
    void ticketCreateThenUpdateWritesTwoEntriesAndIsAppendOnly() throws Exception {
        long ticketId = createTicket("Bug");

        // Update the ticket -> should APPEND an UPDATE entry, not mutate the CREATE entry.
        mockMvc.perform(patch("/tickets/{id}", ticketId)
                        .with(asActor).contentType(APPLICATION_JSON).content("""
                                {"title":"Bug edited"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-logs")
                        .param("entityType", "TICKET").param("entityId", String.valueOf(ticketId)).with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].action").value(hasItem("CREATE")))
                .andExpect(jsonPath("$[*].action").value(hasItem("UPDATE")));
    }

    @Test
    void deleteUserWritesDeleteEntry() throws Exception {
        long victimId = createUser("victim");

        mockMvc.perform(delete("/users/{id}", victimId).with(asActor))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-logs")
                        .param("entityType", "USER").param("action", "DELETE")
                        .param("entityId", String.valueOf(victimId)).with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("DELETE"));
    }

    @Test
    void filterByActionReturnsOnlyMatchingEntries() throws Exception {
        long userId = createUser("bob");
        mockMvc.perform(post("/users/update/{id}", userId)
                        .with(asActor).contentType(APPLICATION_JSON).content("""
                                {"fullName":"Bob B"}""")) // UPDATE USER
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-logs").param("action", "UPDATE").with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action").value(everyItem(is("UPDATE"))));

        mockMvc.perform(get("/audit-logs").param("action", "CREATE").with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action").value(everyItem(is("CREATE"))));
    }

    @Test
    void filterByEntityTypeReturnsOnlyMatchingEntries() throws Exception {
        createUser("carol");   // CREATE USER
        createTicket("Task");  // CREATE TICKET

        mockMvc.perform(get("/audit-logs").param("entityType", "TICKET").with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entityType").value(everyItem(is("TICKET"))));
    }

    @Test
    void filterByEntityIdReturnsOnlyMatchingEntries() throws Exception {
        long t1 = createTicket("One");
        long t2 = createTicket("Two");

        mockMvc.perform(get("/audit-logs").param("entityId", String.valueOf(t1)).with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityId").value((int) t1));

        mockMvc.perform(get("/audit-logs").param("entityId", String.valueOf(t2)).with(asActor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entityId").value((int) t2));
    }

    @Test
    void readOperationsDoNotWriteAuditEntries() throws Exception {
        createTicket("readme"); // one CREATE entry

        long before = auditLogRepository.count();
        mockMvc.perform(get("/users").with(asActor)).andExpect(status().isOk());
        mockMvc.perform(get("/audit-logs").with(asActor)).andExpect(status().isOk());
        long after = auditLogRepository.count();

        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    @Test
    void auditLogsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/audit-logs").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private long createUser(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","fullName":"%s",
                 "role":"DEVELOPER","password":"secret123"}""".formatted(username, username, username);
        String json = mockMvc.perform(post("/users").with(asActor)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private long createTicket(String title) throws Exception {
        String body = """
                {"title":"%s","priority":"LOW","type":"BUG","projectId":%d}"""
                .formatted(title, project.getId());
        String json = mockMvc.perform(post("/tickets").with(asActor)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }
}
