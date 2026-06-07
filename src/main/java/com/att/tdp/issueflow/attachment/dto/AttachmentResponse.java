package com.att.tdp.issueflow.attachment.dto;

/** Attachment metadata, as returned by {@code POST /tickets/{ticketId}/attachments}. */
public record AttachmentResponse(
        Long id,
        Long ticketId,
        String filename,
        String contentType
) {
}
