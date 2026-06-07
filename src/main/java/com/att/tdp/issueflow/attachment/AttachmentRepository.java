package com.att.tdp.issueflow.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** Resolve an attachment scoped to its owning ticket (prevents cross-ticket deletes). */
    Optional<Attachment> findByIdAndTicketId(Long id, Long ticketId);
}
