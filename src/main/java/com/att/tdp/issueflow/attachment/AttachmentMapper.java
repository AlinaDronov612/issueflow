package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import org.springframework.stereotype.Component;

/** Maps {@link Attachment} entities to their response DTO (metadata only). */
@Component
public class AttachmentMapper {

    public AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getFilename(),
                attachment.getContentType());
    }
}
