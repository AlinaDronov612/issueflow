package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.common.BaseEntity;
import com.att.tdp.issueflow.ticket.Ticket;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A blocker relationship: {@code ticket} is blocked by {@code blockedBy}. Both
 * tickets must exist and belong to the same project. The pair is unique.
 */
@Entity
@Table(name = "ticket_dependencies",
        uniqueConstraints = @UniqueConstraint(name = "uq_ticket_blocked_by",
                columnNames = {"ticket_id", "blocked_by"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDependency extends BaseEntity {

    /** The blocked ticket (the one that has a dependency). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** The blocker (the ticket that must be resolved first). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_by", nullable = false)
    private Ticket blockedBy;
}
