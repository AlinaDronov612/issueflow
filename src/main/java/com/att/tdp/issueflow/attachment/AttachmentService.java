package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** Business cap from CLAUDE.md §6: reject files over 10MB. */
    static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** Allowed content types from CLAUDE.md §6. */
    static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "application/pdf", "text/plain");

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;
    private final AttachmentMapper attachmentMapper;

    /**
     * Attaches an uploaded file to a ticket. Attachments are permitted even on a
     * DONE ticket (open-Q resolution: DONE-immutability covers only the ticket's
     * own fields). Rejects empty files, files over 10MB, and disallowed types.
     */
    @Transactional
    public AttachmentResponse upload(Long ticketId, MultipartFile file) {
        Ticket ticket = activeTicketOrThrow(ticketId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required and must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("file exceeds the maximum allowed size of 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException(
                    "contentType must be one of " + String.join(", ", ALLOWED_CONTENT_TYPES));
        }

        Attachment attachment = attachmentRepository.save(Attachment.builder()
                .ticket(ticket)
                .filename(file.getOriginalFilename())
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .data(readBytes(file))
                .build());
        auditService.record(AuditAction.CREATE, AuditEntityType.ATTACHMENT, attachment.getId());

        return attachmentMapper.toResponse(attachment);
    }

    @Transactional
    public void delete(Long ticketId, Long attachmentId) {
        activeTicketOrThrow(ticketId);
        Attachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment " + attachmentId + " not found on ticket " + ticketId));
        attachmentRepository.delete(attachment);
        auditService.record(AuditAction.DELETE, AuditEntityType.ATTACHMENT, attachmentId);
    }

    private Ticket activeTicketOrThrow(Long ticketId) {
        return ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("could not read the uploaded file");
        }
    }
}
