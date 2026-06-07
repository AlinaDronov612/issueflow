package com.att.tdp.issueflow.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically runs the auto-escalation sweep. The interval defaults to one
 * minute and is configurable via {@code app.escalation.interval-ms}; an initial
 * delay (default one minute) keeps the first run off the application-startup path.
 *
 * <p>Gated by {@code app.escalation.enabled} (default true). Tests set it to false
 * and drive {@link TicketService#escalateOverdueTickets()} directly, so the timer
 * never interferes with deterministic assertions.
 */
@Component
@ConditionalOnProperty(name = "app.escalation.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@RequiredArgsConstructor
public class TicketEscalationScheduler {

    private final TicketService ticketService;

    /** Delegates to the service, which logs the sweep summary (shared with the ADMIN trigger). */
    @Scheduled(
            fixedDelayString = "${app.escalation.interval-ms:60000}",
            initialDelayString = "${app.escalation.initial-delay-ms:60000}")
    public void escalateOverdueTickets() {
        ticketService.escalateOverdueTickets();
    }
}
