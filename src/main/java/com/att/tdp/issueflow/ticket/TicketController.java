package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.auth.AuthPrincipal;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.EscalationResult;
import com.att.tdp.issueflow.ticket.dto.TicketImportSummary;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Ticket endpoints. Paths/methods match the README contract (200 OK on create,
 * PATCH for update, {@code ?projectId=} on the list). DELETE is a soft delete;
 * listing/restoring soft-deleted tickets is ADMIN-only. Also serves CSV
 * export/import ({@code GET /tickets/export}, {@code POST /tickets/import}).
 *
 * <p>The {@code {ticketId}} path variable is constrained to digits
 * ({@code :\\d+}). A literal segment already out-ranks a path variable by pattern
 * specificity, but the constraint removes the id route as a candidate for
 * non-numeric segments entirely, so literal sub-paths such as {@code /export},
 * {@code /import}, and {@code /deleted} can never be shadowed (which would
 * otherwise surface as a 400 "invalid value for ticketId").
 */
@Tag(name = "Tickets", description = "Create, update, soft-delete tickets; CSV export/import; escalation")
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public List<TicketResponse> getTicketsByProject(@RequestParam Long projectId) {
        return ticketService.getTicketsByProject(projectId);
    }

    @GetMapping("/{ticketId:\\d+}")
    public TicketResponse getTicket(@PathVariable Long ticketId) {
        return ticketService.getTicket(ticketId);
    }

    @PostMapping
    public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request);
    }

    @PatchMapping("/{ticketId:\\d+}")
    public void updateTicket(@PathVariable Long ticketId,
                             @Valid @RequestBody UpdateTicketRequest request) {
        ticketService.updateTicket(ticketId, request);
    }

    @DeleteMapping("/{ticketId:\\d+}")
    public void deleteTicket(@PathVariable Long ticketId) {
        ticketService.deleteTicket(ticketId);
    }

    @GetMapping("/deleted")
    public List<TicketResponse> getDeletedTickets(@RequestParam Long projectId,
                                                  @AuthenticationPrincipal AuthPrincipal principal) {
        return ticketService.getDeletedTickets(projectId, principal);
    }

    @PostMapping("/{ticketId:\\d+}/restore")
    public void restoreTicket(@PathVariable Long ticketId,
                              @AuthenticationPrincipal AuthPrincipal principal) {
        ticketService.restoreTicket(ticketId, principal);
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportTickets(@RequestParam Long projectId) {
        String csv = ticketService.exportTicketsToCsv(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tickets-project-" + projectId + ".csv\"")
                .body(csv);
    }

    @PostMapping("/import")
    public TicketImportSummary importTickets(@RequestParam Long projectId,
                                             @RequestParam("file") MultipartFile file) {
        return ticketService.importTicketsFromCsv(projectId, file);
    }

    /**
     * On-demand auto-escalation sweep (ADMIN only). The scheduler runs this
     * automatically; this endpoint lets operators/tests trigger it without waiting
     * for the timer. The literal {@code /escalate} cannot collide with the
     * digit-constrained {@code /{ticketId:\\d+}} route.
     */
    @PostMapping("/escalate")
    public EscalationResult escalate(@AuthenticationPrincipal AuthPrincipal principal) {
        return new EscalationResult(ticketService.triggerEscalation(principal));
    }
}
