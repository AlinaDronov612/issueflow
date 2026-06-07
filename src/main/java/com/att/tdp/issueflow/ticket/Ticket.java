package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.BaseEntity;
import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A work item (issue) belonging to exactly one project.
 *
 * <p>Step 1 = persistence only. The escalation fields ({@code overdue},
 * {@code priorityManuallySet}) and {@code deleted} exist now but their logic is
 * deferred: status-transition rules and DONE-immutability come in Ticket Step 2,
 * auto-assign/escalation and soft-delete in their own phases.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    // Set on create, immutable thereafter (also absent from the PATCH contract).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    private Instant dueDate;

    /** Escalation flag (exposed as isOverdue). Logic deferred to the escalation phase. */
    @Column(nullable = false)
    private boolean overdue;

    /** Escalation state: a manual priority change resets it. Logic deferred. */
    @Column(nullable = false)
    private boolean priorityManuallySet;

    /** Soft-delete flag. Filtering/restore deferred to the soft-delete phase. */
    @Column(nullable = false)
    private boolean deleted;

    /** Optimistic locking for concurrent-update protection (enforced in Step 2). */
    @Version
    private Long version;
}
