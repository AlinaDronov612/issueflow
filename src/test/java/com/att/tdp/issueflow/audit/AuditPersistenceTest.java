package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserService;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that an audit entry is actually COMMITTED inside the action's
 * transaction and is therefore visible from a separate, later transaction —
 * i.e. the real HTTP-request boundary, not a single shared test transaction.
 *
 * <p>The existing {@code AuditLogControllerTest} runs write+read in one
 * {@code @Transactional} test, so it cannot detect an audit write that fails to
 * commit. This test deliberately uses separate committed transactions (no
 * {@code @Transactional} rollback) and cleans up its own rows.
 */
@SpringBootTest
class AuditPersistenceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private Long actorId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        cleanAll();
        actorId = tx.execute(s -> userRepository.save(User.builder()
                .username("actor").email("actor@example.com").fullName("Actor")
                .role(Role.ADMIN).passwordHash("x").build()).getId());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanAll();
    }

    @Test
    void auditEntryCommitsWithinActionTransactionAndIsVisibleFromAnotherTransaction() {
        authenticateAs(actorId);

        // Action runs and commits in its own transaction (like a real request).
        Long createdUserId = tx.execute(s -> userService.createUser(new CreateUserRequest(
                "newbie", "newbie@example.com", "New Bie", Role.DEVELOPER, "secret123")).id());

        SecurityContextHolder.clearContext();

        // Read in a SEPARATE transaction: the entry must have committed.
        List<AuditLog> logs = tx.execute(s -> auditLogRepository.findAll());
        assertThat(logs).hasSize(1);

        AuditLog entry = logs.get(0);
        assertThat(entry.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.getEntityType()).isEqualTo(AuditEntityType.USER);
        assertThat(entry.getEntityId()).isEqualTo(createdUserId);
        assertThat(entry.getPerformedBy()).isEqualTo(actorId); // sourced from the principal
        assertThat(entry.getCreatedAt()).isNotNull();          // timestamp populated
    }

    private void authenticateAs(Long userId) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(userId, "actor", Role.ADMIN),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(ctx);
    }

    private void cleanAll() {
        tx.executeWithoutResult(s -> {
            auditLogRepository.deleteAll();
            userRepository.deleteAll();
        });
    }
}
