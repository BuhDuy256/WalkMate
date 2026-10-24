package com.walkmate.application.walkintent;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Runs the intent expiry sweep every 60 seconds.
 *
 * <p>Discovers candidate intent ids outside any transaction, then delegates each id to
 * {@link MatchingCommandService#expireIntent(String)}, which rediscoveres participants and
 * acquires locks in a fresh transaction. Stale entity objects are never passed across
 * transaction boundaries.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentScheduler {

    private static final Duration EXPIRY_BUFFER = Duration.ofMinutes(5);

    private final WalkIntentRepository    walkIntentRepository;
    private final MatchingCommandService  matchingCommandService;

    @Scheduled(fixedDelay = 60_000)
    public void handleExpiredIntents() {
        Instant now = Instant.now();
        log.debug("IntentScheduler: starting intent expiry sweep");

        // GAP-14: intents within 5 min of their scheduled start (window not yet ended).
        try {
            List<String> aboutToStart = walkIntentRepository.findIntentsExpiringSoon(now, EXPIRY_BUFFER)
                    .stream().map(WalkIntent::getId).toList();
            aboutToStart.forEach(this::expireSafely);
        } catch (Exception e) {
            log.error("IntentScheduler: pre-start expiry sweep failed unexpectedly", e);
        }

        // GAP-13: intents whose time window has fully passed.
        try {
            List<String> pastEnd = walkIntentRepository.findOverdueOpenIntents(now)
                    .stream().map(WalkIntent::getId).toList();
            pastEnd.forEach(this::expireSafely);
        } catch (Exception e) {
            log.error("IntentScheduler: overdue expiry sweep failed unexpectedly", e);
        }
    }

    private void expireSafely(String intentId) {
        try {
            matchingCommandService.expireIntent(intentId);
        } catch (Exception e) {
            log.error("IntentScheduler: failed to expire intent={} error={}", intentId, e.getMessage());
        }
    }
}
