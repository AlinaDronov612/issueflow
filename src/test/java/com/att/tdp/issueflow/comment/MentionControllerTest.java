package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.auth.AuthPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class MentionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private User jdoe;
    private User asmith;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User owner = saveUser("owner", "Owner", Role.ADMIN);
        author = saveUser("dev", "Dev", Role.DEVELOPER);
        jdoe = saveUser("jdoe", "John Doe", Role.DEVELOPER);
        asmith = saveUser("asmith", "Alice Smith", Role.DEVELOPER);
        Project project = projectRepository.save(
                Project.builder().name("Proj").description("d").owner(owner).build());
        ticket = saveTicket(project);
    }

    @Test
    void mentionsAreParsedAndReturnedInResponse() throws Exception {
        postComment("Hey @jdoe and @asmith, look at this")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers.length()").value(2))
                .andExpect(jsonPath("$.mentionedUsers[*].username",
                        org.hamcrest.Matchers.containsInAnyOrder("jdoe", "asmith")));
    }

    @Test
    void mentionMatchingIsCaseInsensitive() throws Exception {
        postComment("ping @JDOE thanks")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers.length()").value(1))
                .andExpect(jsonPath("$.mentionedUsers[0].username").value("jdoe"))
                .andExpect(jsonPath("$.mentionedUsers[0].fullName").value("John Doe"));
    }

    @Test
    void unknownUsernameIsIgnored() throws Exception {
        postComment("hello @nobody and @jdoe")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers.length()").value(1))
                .andExpect(jsonPath("$.mentionedUsers[0].username").value("jdoe"));
    }

    @Test
    void mentionsAreReEvaluatedOnEdit() throws Exception {
        // Create mentioning jdoe; edit to mention asmith instead.
        Comment comment = seedComment("first cut @jdoe", jdoe);

        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticket.getId(), comment.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"content":"now @asmith"}"""))
                .andExpect(status().isOk());

        // The comment now mentions only asmith.
        mockMvc.perform(get("/tickets/{tid}/comments", ticket.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mentionedUsers.length()").value(1))
                .andExpect(jsonPath("$[0].mentionedUsers[0].username").value("asmith"));

        // jdoe no longer has any mentions; asmith has one.
        mockMvc.perform(get("/users/{id}/mentions", jdoe.getId()))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/users/{id}/mentions", asmith.getId()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void mentionsListingReturnsCommentsForUser() throws Exception {
        seedComment("a @jdoe", jdoe);

        mockMvc.perform(get("/users/{id}/mentions", jdoe.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("a @jdoe"))
                .andExpect(jsonPath("$.data[0].mentionedUsers[0].username").value("jdoe"));
    }

    @Test
    void mentionsListingIsPaginated() throws Exception {
        seedComment("one @jdoe", jdoe);
        seedComment("two @jdoe", jdoe);
        seedComment("three @jdoe", jdoe);

        mockMvc.perform(get("/users/{id}/mentions", jdoe.getId())
                        .param("page", "1").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/users/{id}/mentions", jdoe.getId())
                        .param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void mentionsListingForUnknownUserReturns404() throws Exception {
        mockMvc.perform(get("/users/{id}/mentions", 99999))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions postComment(String content) throws Exception {
        String body = """
                {"authorId":%d,"content":%s}""".formatted(author.getId(), quote(content));
        return mockMvc.perform(post("/tickets/{tid}/comments", ticket.getId())
                .with(asUser(author)).contentType(APPLICATION_JSON).content(body));
    }

    private String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private Comment seedComment(String content, User... mentioned) {
        Comment comment = Comment.builder()
                .ticket(ticket).author(author).content(content).build();
        comment.getMentionedUsers().addAll(List.of(mentioned));
        return commentRepository.save(comment);
    }

    private User saveUser(String username, String fullName, Role role) {
        return userRepository.save(User.builder()
                .username(username).email(username + "@example.com").fullName(fullName)
                .role(role).passwordHash("x").build());
    }

    private Ticket saveTicket(Project project) {
        return ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(Status.TODO).priority(Priority.MEDIUM)
                .type(TicketType.BUG).project(project)
                .overdue(false).priorityManuallySet(false).deleted(false).build());
    }

    private RequestPostProcessor asUser(User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
