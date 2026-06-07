package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User owner = userRepository.save(User.builder().username("owner").email("o@e.com")
                .fullName("Owner").role(Role.ADMIN).passwordHash("x").build());
        Project project = projectRepository.save(
                Project.builder().name("P").description("d").owner(owner).build());
        ticket = seedTicket(project, false);
    }

    @Test
    void uploadReturnsMetadataAndPersists() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "screenshot.png", "image/png", "fake-png-bytes".getBytes());

        mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId()).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$.filename").value("screenshot.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"));

        assertThat(attachmentRepository.findAll()).hasSize(1);
    }

    @Test
    void uploadRejectsFileOver10Mb() throws Exception {
        byte[] tooBig = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", tooBig);

        mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId()).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("10MB")));

        assertThat(attachmentRepository.findAll()).isEmpty();
    }

    @Test
    void uploadRejectsDisallowedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip", "data".getBytes());

        mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId()).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("contentType")));

        assertThat(attachmentRepository.findAll()).isEmpty();
    }

    @Test
    void uploadRejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId()).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("file is required")));
    }

    @Test
    void uploadToSoftDeletedTicketReturns404() throws Exception {
        Ticket deleted = seedTicket(ticket.getProject(), true);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hi".getBytes());

        mockMvc.perform(multipart("/tickets/{ticketId}/attachments", deleted.getId()).file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesAttachment() throws Exception {
        Attachment saved = attachmentRepository.save(Attachment.builder()
                .ticket(ticket).filename("doc.pdf").contentType("application/pdf")
                .sizeBytes(3).data("pdf".getBytes()).build());

        mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}",
                        ticket.getId(), saved.getId()))
                .andExpect(status().isOk());

        assertThat(attachmentRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deleteNonexistentAttachmentReturns404() throws Exception {
        mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}",
                        ticket.getId(), 9999L))
                .andExpect(status().isNotFound());
    }

    private Ticket seedTicket(Project project, boolean deleted) {
        return ticketRepository.save(Ticket.builder()
                .title("T").description("d").status(Status.TODO).priority(Priority.MEDIUM)
                .type(TicketType.BUG).project(project)
                .overdue(false).priorityManuallySet(false).deleted(deleted).build());
    }
}
