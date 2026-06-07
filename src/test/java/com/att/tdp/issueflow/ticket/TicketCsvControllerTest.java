package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not {@code @Transactional}: import persists each row in its own REQUIRES_NEW
 * transaction, which would be invisible to (and uncommitted by) a test-scoped
 * transaction. Setup data is committed and cleaned up per test via {@code deleteAll}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TicketCsvControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private User assignee;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        User owner = userRepository.save(User.builder().username("owner").email("o@e.com")
                .fullName("Owner").role(Role.ADMIN).passwordHash("x").build());
        assignee = userRepository.save(User.builder().username("dev").email("d@e.com")
                .fullName("Dev").role(Role.DEVELOPER).passwordHash("x").build());
        project = projectRepository.save(
                Project.builder().name("P").description("d").owner(owner).build());
    }

    @AfterEach
    void tearDown() {
        // This class is not @Transactional, so committed rows must be cleaned up
        // (FK-safe order) to avoid polluting other test classes' shared context.
        ticketRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void exportQuotesAndRoundTripsCommasAndQuotes() throws Exception {
        // A title containing both a comma and a double-quote must survive export.
        String trickyTitle = "Fix \"login\", urgently";
        seedTicket(trickyTitle, "desc,with,commas", Status.TODO, Priority.HIGH, TicketType.BUG);

        String csv = mockMvc.perform(get("/tickets/export").param("projectId", id(project)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Parse the produced CSV back with Commons CSV and confirm the field is intact.
        try (CSVParser parser = CSVParser.parse(csv,
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            List<CSVRecord> records = parser.getRecords();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).get("title")).isEqualTo(trickyTitle);
            assertThat(records.get(0).get("description")).isEqualTo("desc,with,commas");
        }
    }

    @Test
    void exportExcludesSoftDeletedTickets() throws Exception {
        seedTicket("Active", "d", Status.TODO, Priority.LOW, TicketType.BUG);
        Ticket deleted = seedTicket("Gone", "d", Status.TODO, Priority.LOW, TicketType.BUG);
        deleted.setDeleted(true);
        ticketRepository.save(deleted);

        String csv = mockMvc.perform(get("/tickets/export").param("projectId", id(project)))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("Active").doesNotContain("Gone");
    }

    @Test
    void importCreatesValidRows() throws Exception {
        String csv = """
                id,title,description,status,priority,type,assigneeId
                ,Implement login,Login page,TODO,HIGH,FEATURE,%d
                ,Fix crash,Null pointer,IN_PROGRESS,CRITICAL,BUG,
                """.formatted(assignee.getId());

        mockMvc.perform(importCsv(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.errors.length()").value(0));

        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedFalse(project.getId());
        assertThat(tickets).hasSize(2);
        assertThat(tickets).anySatisfy(t -> {
            assertThat(t.getTitle()).isEqualTo("Implement login");
            assertThat(t.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(t.getType()).isEqualTo(TicketType.FEATURE);
            assertThat(t.getAssignee().getId()).isEqualTo(assignee.getId());
        });
    }

    @Test
    void importReportsBadRowsWithoutAborting() throws Exception {
        String csv = """
                id,title,description,status,priority,type,assigneeId
                ,Good ticket,ok,TODO,LOW,BUG,
                ,Bad priority,x,TODO,URGENT,BUG,
                ,,missing title,TODO,LOW,BUG,
                ,Bad assignee,x,TODO,LOW,BUG,999999
                """;

        mockMvc.perform(importCsv(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(3))
                .andExpect(jsonPath("$.errors.length()").value(3));

        // Only the one valid row was persisted; bad rows did not abort the import.
        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedFalse(project.getId());
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getTitle()).isEqualTo("Good ticket");
    }

    @Test
    void importRejectsInvalidEnumWithInformativeError() throws Exception {
        String csv = """
                id,title,description,status,priority,type,assigneeId
                ,Weird status,x,SHIPPED,LOW,BUG,
                """;

        mockMvc.perform(importCsv(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0]",
                        org.hamcrest.Matchers.containsString("status must be one of")));
    }

    @Test
    void importReportsOverLengthTitleRowWithoutAborting() throws Exception {
        // A >255-char title passes @NotBlank but exceeds the @Size(255) cap, so it is
        // reported as a per-row error (caught at validation, never at DB flush) while
        // the valid row still persists.
        String longTitle = "x".repeat(300);
        String csv = "id,title,description,status,priority,type,assigneeId\n"
                + ",Valid one,d,TODO,LOW,BUG,\n"
                + "," + longTitle + ",d,TODO,LOW,BUG,\n";

        mockMvc.perform(importCsv(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0]",
                        org.hamcrest.Matchers.containsString("title")));

        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedFalse(project.getId());
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getTitle()).isEqualTo("Valid one");
    }

    @Test
    void exportThenImportRoundTripsIntoAnotherProject() throws Exception {
        String trickyTitle = "Edge \"case\", really";
        seedTicket(trickyTitle, "d", Status.IN_REVIEW, Priority.MEDIUM, TicketType.TECHNICAL);

        String csv = mockMvc.perform(get("/tickets/export").param("projectId", id(project)))
                .andReturn().getResponse().getContentAsString();

        Project target = projectRepository.save(Project.builder()
                .name("Target").description("d").owner(assignee).build());

        mockMvc.perform(importCsv(csv, target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        List<Ticket> imported = ticketRepository.findByProjectIdAndDeletedFalse(target.getId());
        assertThat(imported).hasSize(1);
        assertThat(imported.get(0).getTitle()).isEqualTo(trickyTitle);
        assertThat(imported.get(0).getStatus()).isEqualTo(Status.IN_REVIEW);
    }

    @Test
    void importToUnknownProjectReturns404() throws Exception {
        String csv = "id,title,description,status,priority,type,assigneeId\n,T,d,TODO,LOW,BUG,\n";
        mockMvc.perform(importCsv(csv, 999999L))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.RequestBuilder importCsv(String csv) {
        return importCsv(csv, project.getId());
    }

    private org.springframework.test.web.servlet.RequestBuilder importCsv(String csv, Long projectId) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "tickets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return multipart("/tickets/import").file(file).param("projectId", String.valueOf(projectId));
    }

    private Ticket seedTicket(String title, String description, Status status,
                              Priority priority, TicketType type) {
        return ticketRepository.save(Ticket.builder()
                .title(title).description(description).status(status).priority(priority)
                .type(type).project(project)
                .overdue(false).priorityManuallySet(false).deleted(false).build());
    }

    private String id(Project p) {
        return String.valueOf(p.getId());
    }
}
