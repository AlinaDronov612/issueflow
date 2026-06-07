package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.BaseEntity;
import com.att.tdp.issueflow.ticket.Ticket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A file attached to a ticket. The raw bytes are stored in the database
 * ({@code @Lob}) alongside their metadata, so the feature is self-contained and
 * behaves identically under H2 (tests) and PostgreSQL (runtime). The README only
 * exposes the metadata ({@code id, ticketId, filename, contentType}); there is no
 * download endpoint in the contract.
 */
@Entity
@Table(name = "attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    // @Lob lifts the 255-byte default length; VARBINARY forces a bytea/binary
    // column instead of the PostgreSQL dialect's default BLOB->oid mapping, which
    // H2's PG-compat mode renders as INTEGER and cannot store bytes.
    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] data;
}
