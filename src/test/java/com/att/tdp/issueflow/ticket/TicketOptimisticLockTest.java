package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.common.exception.ApiError;
import com.att.tdp.issueflow.common.exception.GlobalExceptionHandler;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
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
 * A genuine two-transaction optimistic-locking race: the same ticket is loaded
 * twice (both at the same {@code @Version}), the first update commits, and the
 * second commit against the now-stale version fails. We then confirm the
 * resulting exception maps to HTTP 409 via the global handler.
 *
 * <p>This test deliberately does NOT use {@code @Transactional} rollback — it
 * needs real, separately-committed transactions — so it cleans its own
 * committed rows in {@code @AfterEach}.
 */
@SpringBootTest
class TicketOptimisticLockTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        cleanAll();
        ticketId = tx.execute(s -> {
            User owner = userRepository.save(User.builder()
                    .username("owner").email("owner@example.com").fullName("Owner")
                    .role(Role.ADMIN).passwordHash("x").build());
            Project project = projectRepository.save(Project.builder()
                    .name("Proj").description("d").owner(owner).build());
            Ticket ticket = ticketRepository.save(Ticket.builder()
                    .title("Race").description("d").status(Status.TODO).priority(Priority.LOW)
                    .type(TicketType.BUG).project(project)
                    .overdue(false).priorityManuallySet(false).deleted(false).build());
            return ticket.getId();
        });
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    @Test
    void secondCommitAgainstStaleVersionFailsAndMapsTo409() {
        // Two independent reads in separate transactions: same version loaded twice.
        Ticket copyA = tx.execute(s -> ticketRepository.findById(ticketId).orElseThrow());
        Ticket copyB = tx.execute(s -> ticketRepository.findById(ticketId).orElseThrow());
        assertThat(copyA.getVersion()).isEqualTo(copyB.getVersion());

        // First writer commits successfully -> version advances in the DB.
        tx.executeWithoutResult(s -> {
            copyA.setTitle("updated by A");
            ticketRepository.saveAndFlush(copyA);
        });

        // Second writer commits against the stale version -> optimistic-lock failure.
        ObjectOptimisticLockingFailureException ex = catchThrowableOfType(
                () -> tx.executeWithoutResult(s -> {
                    copyB.setTitle("updated by B");
                    ticketRepository.saveAndFlush(copyB);
                }),
                ObjectOptimisticLockingFailureException.class);
        assertThat(ex).as("stale second commit must fail").isNotNull();

        // The first writer's change is the one that persisted.
        Ticket persisted = tx.execute(s -> ticketRepository.findById(ticketId).orElseThrow());
        assertThat(persisted.getTitle()).isEqualTo("updated by A");

        // And the global handler turns that real exception into a 409.
        ResponseEntity<ApiError> response =
                new GlobalExceptionHandler().handleOptimisticLock(ex, new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    private void cleanAll() {
        tx.executeWithoutResult(s -> {
            ticketRepository.deleteAll();
            projectRepository.deleteAll();
            userRepository.deleteAll();
        });
    }
}
