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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the literal sub-paths under {@code /tickets} against being shadowed by
 * the {@code /tickets/{ticketId}} route. If precedence ever collides, these
 * requests resolve to the id handler and fail (400 type-mismatch) instead of
 * their own handlers.
 *
 * <p>Not {@code @Transactional}: the import path persists rows in their own
 * REQUIRES_NEW transaction, which would not see test-transaction setup data.
 * Setup is committed and cleaned per test via {@code deleteAll}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TicketRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private RequestPostProcessor asAdmin;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User admin = userRepository.save(User.builder().username("routeadmin").email("a@e.com")
                .fullName("Admin").role(Role.ADMIN).passwordHash("x").build());
        project = projectRepository.save(
                Project.builder().name("P").description("d").owner(admin).build());
        asAdmin = asUser(admin);
    }

    @AfterEach
    void tearDown() {
        // Not @Transactional: clean committed rows (FK-safe order) so this class
        // does not pollute other test classes' shared context.
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void exportPathResolvesToExportHandlerAndReturnsCsv() throws Exception {
        mockMvc.perform(get("/tickets/export").param("projectId", String.valueOf(project.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".csv")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.startsWith("id,title,description,status,priority,type,assigneeId")));
    }

    @Test
    void deletedPathResolvesToDeletedHandler() throws Exception {
        mockMvc.perform(get("/tickets/deleted")
                        .with(asAdmin).param("projectId", String.valueOf(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void importPathResolvesToImportHandler() throws Exception {
        String csv = "id,title,description,status,priority,type,assigneeId\n,T,d,TODO,LOW,BUG,\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "t.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tickets/import")
                        .file(file).param("projectId", String.valueOf(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void numericIdStillResolvesToGetTicketHandler() throws Exception {
        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(Status.TODO).priority(Priority.LOW)
                .type(TicketType.BUG).project(project)
                .overdue(false).priorityManuallySet(false).deleted(false).build());

        mockMvc.perform(get("/tickets/{id}", ticket.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.getId()))
                .andExpect(jsonPath("$.title").value("T"));
    }

    private RequestPostProcessor asUser(User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
