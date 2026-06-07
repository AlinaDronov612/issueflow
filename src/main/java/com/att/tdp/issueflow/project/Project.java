package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.common.BaseEntity;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * A top-level container grouping related tickets.
 *
 * <p>Soft delete: {@code DELETE /projects/{id}} sets {@code deleted=true};
 * normal reads filter it out; ADMIN can list/restore via {@code /projects/deleted}
 * and {@code /projects/{id}/restore}.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Soft-delete flag. {@code @ColumnDefault} lets ddl-auto add the NOT-NULL column to existing rows. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean deleted;
}
