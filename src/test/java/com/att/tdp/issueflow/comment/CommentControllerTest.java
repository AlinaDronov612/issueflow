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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
@WithMockUser // satisfies authentication for endpoints that don't read the principal
@Transactional // roll back each test for isolation across the shared context
class CommentControllerTest {

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
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        User owner = userRepository.save(User.builder()
                .username("owner").email("owner@example.com").fullName("Owner")
                .role(Role.ADMIN).passwordHash("x").build());
        author = userRepository.save(User.builder()
                .username("dev").email("dev@example.com").fullName("Dev")
                .role(Role.DEVELOPER).passwordHash("x").build());
        Project project = projectRepository.save(Project.builder()
                .name("Proj").description("d").owner(owner).build());
        ticket = saveTicket(project, Status.TODO);
    }

    @Test
    void addCommentReturns200WithEmptyMentions() throws Exception {
        String body = """
                {"authorId":%d,"content":"Hello there"}""".formatted(author.getId());

        mockMvc.perform(post("/tickets/{tid}/comments", ticket.getId())
                        .with(asUser(author)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId().intValue()))
                .andExpect(jsonPath("$.authorId").value(author.getId().intValue()))
                .andExpect(jsonPath("$.content").value("Hello there"))
                .andExpect(jsonPath("$.mentionedUsers").isArray())
                .andExpect(jsonPath("$.mentionedUsers.length()").value(0));
    }

    @Test
    void addCommentWithMismatchedAuthorReturns403() throws Exception {
        String body = """
                {"authorId":%d,"content":"x"}""".formatted(author.getId() + 999);

        mockMvc.perform(post("/tickets/{tid}/comments", ticket.getId())
                        .with(asUser(author)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("authorId")));
    }

    @Test
    void addCommentWithBlankContentReturns400() throws Exception {
        String body = """
                {"authorId":%d,"content":""}""".formatted(author.getId());

        mockMvc.perform(post("/tickets/{tid}/comments", ticket.getId())
                        .with(asUser(author)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("content")));
    }

    @Test
    void addCommentWithMissingAuthorIdReturns400() throws Exception {
        mockMvc.perform(post("/tickets/{tid}/comments", ticket.getId())
                        .with(asUser(author)).contentType(APPLICATION_JSON).content("""
                                {"content":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("authorId")));
    }

    @Test
    void addCommentToUnknownTicketReturns404() throws Exception {
        String body = """
                {"authorId":%d,"content":"x"}""".formatted(author.getId());

        mockMvc.perform(post("/tickets/{tid}/comments", 99999)
                        .with(asUser(author)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void addCommentIsAllowedOnDoneTicket() throws Exception {
        Ticket doneTicket = saveTicket(ticket.getProject(), Status.DONE);
        String body = """
                {"authorId":%d,"content":"comment on done"}""".formatted(author.getId());

        mockMvc.perform(post("/tickets/{tid}/comments", doneTicket.getId())
                        .with(asUser(author)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("comment on done"));
    }

    @Test
    void getCommentsReturnsList() throws Exception {
        seedComment("first");
        seedComment("second");

        mockMvc.perform(get("/tickets/{tid}/comments", ticket.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getCommentsForUnknownTicketReturns404() throws Exception {
        mockMvc.perform(get("/tickets/{tid}/comments", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCommentReturns200() throws Exception {
        Comment comment = seedComment("original");

        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticket.getId(), comment.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"content":"edited"}"""))
                .andExpect(status().isOk());

        assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent())
                .isEqualTo("edited");
    }

    @Test
    void updateCommentBlankContentReturns400() throws Exception {
        Comment comment = seedComment("original");

        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticket.getId(), comment.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"content":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("content")));
    }

    @Test
    void updateUnknownCommentReturns404() throws Exception {
        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticket.getId(), 99999)
                        .contentType(APPLICATION_JSON).content("""
                                {"content":"x"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCommentUnderWrongTicketReturns404() throws Exception {
        Comment comment = seedComment("original");
        Ticket otherTicket = saveTicket(ticket.getProject(), Status.TODO);

        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", otherTicket.getId(), comment.getId())
                        .contentType(APPLICATION_JSON).content("""
                                {"content":"x"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCommentReturns200() throws Exception {
        Comment comment = seedComment("original");

        mockMvc.perform(delete("/tickets/{tid}/comments/{cid}", ticket.getId(), comment.getId()))
                .andExpect(status().isOk());

        assertThat(commentRepository.existsById(comment.getId())).isFalse();
    }

    @Test
    void deleteUnknownCommentReturns404() throws Exception {
        mockMvc.perform(delete("/tickets/{tid}/comments/{cid}", ticket.getId(), 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCommentUnderWrongTicketReturns404() throws Exception {
        Comment comment = seedComment("original");
        Ticket otherTicket = saveTicket(ticket.getProject(), Status.TODO);

        mockMvc.perform(delete("/tickets/{tid}/comments/{cid}", otherTicket.getId(), comment.getId()))
                .andExpect(status().isNotFound());

        assertThat(commentRepository.existsById(comment.getId())).isTrue(); // unchanged
    }

    @Test
    void commentsOnSoftDeletedTicketReturn404() throws Exception {
        ticket.setDeleted(true);
        ticketRepository.save(ticket);

        // Listing a soft-deleted ticket's comments must 404 (hidden like the ticket).
        mockMvc.perform(get("/tickets/{tid}/comments", ticket.getId()))
                .andExpect(status().isNotFound());

        // Posting to it must 404 too (so it can't leak via /users/:id/mentions).
        String body = """
                {"authorId":%d,"content":"@dev hi"}""".formatted(author.getId());
        mockMvc.perform(post("/tickets/{tid}/comments", ticket.getId())
                        .with(asUser(author)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/tickets/{tid}/comments", ticket.getId()).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    private Comment seedComment(String content) {
        return commentRepository.save(Comment.builder()
                .ticket(ticket).author(author).content(content).build());
    }

    private Ticket saveTicket(Project project, Status status) {
        return ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(status).priority(Priority.MEDIUM)
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
