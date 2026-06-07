package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.common.exception.ApiError;
import com.att.tdp.issueflow.common.exception.GlobalExceptionHandler;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A genuine two-transaction optimistic-locking race on comment edit: two users
 * load the same comment (same {@code @Version}), the first edit commits, and the
 * second edit against the now-stale version fails and maps to HTTP 409.
 *
 * <p>Like {@code TicketOptimisticLockTest}, this uses real separately-committed
 * transactions (no {@code @Transactional} rollback) and cleans up its own rows.
 */
@SpringBootTest
class CommentOptimisticLockTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long commentId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        cleanAll();
        commentId = tx.execute(s -> {
            User author = userRepository.save(User.builder()
                    .username("dev").email("dev@example.com").fullName("Dev")
                    .role(Role.DEVELOPER).passwordHash("x").build());
            Project project = projectRepository.save(Project.builder()
                    .name("Proj").description("d").owner(author).build());
            Ticket ticket = ticketRepository.save(Ticket.builder()
                    .title("T").description("d").status(Status.TODO).priority(Priority.LOW)
                    .type(TicketType.BUG).project(project)
                    .overdue(false).priorityManuallySet(false).deleted(false).build());
            Comment comment = commentRepository.save(Comment.builder()
                    .ticket(ticket).author(author).content("original").build());
            return comment.getId();
        });
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    @Test
    void concurrentCommentEditSecondCommitFailsAndMapsTo409() {
        Comment copyA = tx.execute(s -> commentRepository.findById(commentId).orElseThrow());
        Comment copyB = tx.execute(s -> commentRepository.findById(commentId).orElseThrow());
        assertThat(copyA.getVersion()).isEqualTo(copyB.getVersion());

        // First editor commits successfully.
        tx.executeWithoutResult(s -> {
            copyA.setContent("edited by A");
            commentRepository.saveAndFlush(copyA);
        });

        // Second editor commits against the stale version -> optimistic-lock failure.
        ObjectOptimisticLockingFailureException ex = catchThrowableOfType(
                () -> tx.executeWithoutResult(s -> {
                    copyB.setContent("edited by B");
                    commentRepository.saveAndFlush(copyB);
                }),
                ObjectOptimisticLockingFailureException.class);
        assertThat(ex).as("stale second edit must fail").isNotNull();

        Comment persisted = tx.execute(s -> commentRepository.findById(commentId).orElseThrow());
        assertThat(persisted.getContent()).isEqualTo("edited by A");

        ResponseEntity<ApiError> response =
                new GlobalExceptionHandler().handleOptimisticLock(ex, new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    private void cleanAll() {
        tx.executeWithoutResult(s -> {
            commentRepository.deleteAll();
            ticketRepository.deleteAll();
            projectRepository.deleteAll();
            userRepository.deleteAll();
        });
    }
}
