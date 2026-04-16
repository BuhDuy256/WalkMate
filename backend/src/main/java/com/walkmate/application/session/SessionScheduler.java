package com.walkmate.application.session;

import com.walkmate.application.proposal.MatchingCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the session lifecycle sweep every 60 seconds.
 *
 * Delegates all business logic to {@link SessionCommandService#handleExpiredSessions()}.
 * Requires {@code @EnableScheduling} on the application class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionScheduler {

    private final SessionCommandService sessionCommandService;
    private final MatchingCommandService matchingCommandService;

    @Scheduled(fixedDelay = 60_000)
    public void runSessionLifecycleSweep() {
        log.debug("SessionScheduler: starting lifecycle sweep");
        try {
            sessionCommandService.handleExpiredSessions();
        } catch (Exception e) {
            // Swallow to keep the scheduler alive; the exception is already logged inside the service.
            log.error("SessionScheduler: session sweep failed unexpectedly", e);
        }

        try {
            matchingCommandService.sweepExpiredProposals();
        } catch (Exception e) {
            log.error("SessionScheduler: proposal expiry sweep failed unexpectedly", e);
        }
    }
}
