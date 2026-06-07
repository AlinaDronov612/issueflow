package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.common.enums.AuditAction;
import com.att.tdp.issueflow.common.enums.AuditEntityType;
import com.att.tdp.issueflow.common.enums.Priority;
import com.att.tdp.issueflow.common.enums.Role;
import com.att.tdp.issueflow.common.enums.Status;
import com.att.tdp.issueflow.common.enums.TicketType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.dependency.TicketDependencyRepository;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketImportSummary;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.workload.WorkloadService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    /** CSV column order for export/import, matching the README contract. */
    private static final String[] CSV_HEADERS =
            {"id", "title", "description", "status", "priority", "type", "assigneeId"};

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketMapper ticketMapper;
    private final AuditService auditService;
    private final TicketDependencyRepository dependencyRepository;
    private final WorkloadService workloadService;
    private final Validator validator;
    /** Self-reference (lazy, no constructor cycle) so import rows get a real REQUIRES_NEW boundary. */
    private final ObjectProvider<TicketService> self;

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return ticketRepository.findByProjectIdAndDeletedFalse(projectId).stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(Long ticketId) {
        return ticketMapper.toResponse(findActiveOrThrow(ticketId));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getDeletedTickets(Long projectId, AuthPrincipal principal) {
        requireAdmin(principal);
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return ticketRepository.findByProjectIdAndDeletedTrue(projectId).stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        return ticketMapper.toResponse(persistNewTicket(request, project, resolveAssignee(request.assigneeId())));
    }

    /**
     * Builds, saves, and audits a new ticket. Callers must have already resolved
     * the project and (explicit) assignee and validated the request, so this never
     * throws a validation error mid-transaction (important for the per-row CSV import).
     *
     * <p>When no assignee was provided, the ticket is auto-assigned to the
     * least-loaded DEVELOPER in the project (§13). If none exist it stays
     * unassigned (no error). An auto-assignment is recorded as a separate SYSTEM /
     * AUTO_ASSIGN audit entry, in addition to the normal CREATE entry.
     */
    private Ticket persistNewTicket(CreateTicketRequest request, Project project, User assignee) {
        boolean autoAssigned = false;
        if (assignee == null) {
            assignee = workloadService.selectLeastLoadedDeveloper(project).orElse(null);
            autoAssigned = assignee != null;
        }

        Ticket ticket = Ticket.builder()
                .title(request.title())
                .description(request.description())
                .status(request.status() != null ? request.status() : Status.TODO)
                .priority(request.priority())
                .type(request.type())
                .project(project)
                .assignee(assignee)
                .dueDate(request.dueDate())
                .overdue(false)
                .priorityManuallySet(false)
                .deleted(false)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        auditService.record(AuditAction.CREATE, AuditEntityType.TICKET, saved.getId());
        if (autoAssigned) {
            auditService.recordSystem(AuditAction.AUTO_ASSIGN, AuditEntityType.TICKET, saved.getId());
        }
        return saved;
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assigneeId));
    }

    @Transactional
    public void updateTicket(Long ticketId, UpdateTicketRequest request) {
        Ticket ticket = findActiveOrThrow(ticketId);

        // A DONE ticket cannot be updated at all (its own fields).
        if (ticket.getStatus() == Status.DONE) {
            throw new ConflictException("ticket " + ticketId + " is DONE and cannot be updated");
        }

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("title must not be blank");
            }
            ticket.setTitle(request.title());
        }
        if (request.description() != null) {
            ticket.setDescription(request.description());
        }
        if (request.status() != null) {
            validateForwardOnly(ticket.getStatus(), request.status());
            if (request.status() == Status.DONE
                    && dependencyRepository.hasUnresolvedBlockers(ticketId, Status.DONE)) {
                throw new ConflictException("ticket " + ticketId
                        + " cannot move to DONE while it has unresolved (non-DONE) blocking dependencies");
            }
            ticket.setStatus(request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
            // A manual priority change resets escalation state: clear the overdue
            // flag and mark the priority as human-set so the next escalation run
            // does not immediately re-escalate it (see escalateOne).
            ticket.setOverdue(false);
            ticket.setPriorityManuallySet(true);
        }
        if (request.assigneeId() != null) {
            User assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.assigneeId()));
            ticket.setAssignee(assignee);
        }
        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }

        ticketRepository.save(ticket);
        auditService.record(AuditAction.UPDATE, AuditEntityType.TICKET, ticketId);
    }

    /**
     * Status may only move forward in TODO -> IN_PROGRESS -> IN_REVIEW -> DONE
     * (enum declaration order). Same status is a no-op; any move to an earlier
     * status is rejected as a conflict.
     */
    private void validateForwardOnly(Status current, Status target) {
        if (target.ordinal() < current.ordinal()) {
            throw new ConflictException(
                    "illegal status transition: " + current + " -> " + target
                            + " (status can only move forward)");
        }
    }

    /** ADMIN-only on-demand trigger for the escalation sweep (testability/ops). */
    @Transactional
    public int triggerEscalation(AuthPrincipal principal) {
        requireAdmin(principal);
        return escalateOverdueTickets();
    }

    /**
     * Auto-escalation sweep (called by the scheduler and the ADMIN trigger).
     * Promotes each overdue, non-DONE ticket with a due date by one priority level;
     * idempotent at CRITICAL (sets {@code isOverdue}, never escalates further);
     * never changes status. Returns the number of tickets actually escalated.
     */
    @Transactional
    public int escalateOverdueTickets() {
        int escalated = 0;
        for (Ticket ticket : ticketRepository.findEscalationCandidates(Instant.now(), Status.DONE)) {
            if (escalateOne(ticket)) {
                escalated++;
            }
        }
        return escalated;
    }

    /** Applies the escalation rule to one already-overdue, non-DONE ticket. */
    private boolean escalateOne(Ticket ticket) {
        // A manually-set priority gets a one-cycle grace: consume the flag and skip
        // escalation this run, so a human's choice is not immediately overridden.
        if (ticket.isPriorityManuallySet()) {
            ticket.setPriorityManuallySet(false);
            ticketRepository.save(ticket);
            return false;
        }
        // Idempotent at CRITICAL: flag overdue once, never promote past CRITICAL.
        if (ticket.getPriority() == Priority.CRITICAL) {
            if (!ticket.isOverdue()) {
                ticket.setOverdue(true);
                ticketRepository.save(ticket);
            }
            return false;
        }
        // Promote exactly one level; status is left untouched.
        ticket.setPriority(Priority.values()[ticket.getPriority().ordinal() + 1]);
        ticketRepository.save(ticket);
        auditService.recordSystem(AuditAction.AUTO_ESCALATE, AuditEntityType.TICKET, ticket.getId());
        return true;
    }

    /** Soft delete: mark deleted, keep the row. Any authenticated user may delete. */
    @Transactional
    public void deleteTicket(Long ticketId) {
        Ticket ticket = findActiveOrThrow(ticketId);
        ticket.setDeleted(true);
        ticketRepository.save(ticket);
        auditService.record(AuditAction.DELETE, AuditEntityType.TICKET, ticketId);
    }

    /** Restore a soft-deleted ticket (ADMIN only). */
    @Transactional
    public void restoreTicket(Long ticketId, AuthPrincipal principal) {
        requireAdmin(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (!ticket.isDeleted()) {
            throw new BadRequestException("ticket " + ticketId + " is not deleted");
        }
        ticket.setDeleted(false);
        ticketRepository.save(ticket);
        auditService.record(AuditAction.RESTORE, AuditEntityType.TICKET, ticketId);
    }

    /**
     * Exports a project's non-deleted tickets as CSV. Commons CSV quotes/escapes
     * any field containing a comma, quote, or newline, so the output round-trips.
     */
    @Transactional(readOnly = true)
    public String exportTicketsToCsv(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        List<Ticket> tickets = ticketRepository.findByProjectIdAndDeletedFalse(projectId);

        StringWriter out = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build();
        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (Ticket t : tickets) {
                printer.printRecord(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription(),
                        t.getStatus(),
                        t.getPriority(),
                        t.getType(),
                        t.getAssignee() == null ? null : t.getAssignee().getId());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write tickets CSV", e);
        }
        return out.toString();
    }

    /**
     * Imports tickets into a project from a CSV file. Each row is validated with
     * the same rules as normal creation; a bad row is reported in the summary and
     * skipped, never aborting the whole import.
     *
     * <p>Not transactional: each row is persisted in its own REQUIRES_NEW boundary
     * (see {@link #createTicketInNewTransaction}), so a row that fails even at flush
     * time rolls back only itself and cannot mark a shared transaction
     * rollback-only and poison the rest of the batch.
     */
    public TicketImportSummary importTicketsFromCsv(Long projectId, MultipartFile file) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required and must not be empty");
        }

        int created = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            for (CSVRecord record : parser) {
                try {
                    CreateTicketRequest request = parseRow(record, projectId);
                    self.getObject().createTicketInNewTransaction(request);
                    created++;
                } catch (RuntimeException ex) {
                    failed++;
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded CSV file");
        }
        return new TicketImportSummary(created, failed, errors);
    }

    /**
     * Creates one imported ticket in its own transaction. Invoked through the
     * proxy ({@code self.getObject()}) so REQUIRES_NEW actually applies; delegates
     * to {@link #createTicket} for identical creation/auto-assign/audit behavior.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TicketResponse createTicketInNewTransaction(CreateTicketRequest request) {
        return createTicket(request);
    }

    /** Parses one CSV row into a validated {@link CreateTicketRequest} (throws on bad data). */
    private CreateTicketRequest parseRow(CSVRecord record, Long projectId) {
        CreateTicketRequest request = new CreateTicketRequest(
                value(record, "title"),
                value(record, "description"),
                parseEnum(Status.class, value(record, "status"), "status"),
                parseEnum(Priority.class, value(record, "priority"), "priority"),
                parseEnum(TicketType.class, value(record, "type"), "type"),
                projectId,
                parseLong(value(record, "assigneeId"), "assigneeId"),
                null);

        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new BadRequestException(message);
        }
        return request;
    }

    /** Returns a trimmed cell value, or null if the column is absent or blank. */
    private String value(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String raw = record.get(column);
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(Object::toString).collect(Collectors.joining(", "));
            throw new BadRequestException(field + " must be one of " + allowed);
        }
    }

    private Long parseLong(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException(field + " must be a number");
        }
    }

    private Ticket findActiveOrThrow(Long ticketId) {
        return ticketRepository.findByIdAndDeletedFalse(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private void requireAdmin(AuthPrincipal principal) {
        if (principal == null || principal.role() != Role.ADMIN) {
            throw new ForbiddenException("This operation requires ADMIN role");
        }
    }
}
