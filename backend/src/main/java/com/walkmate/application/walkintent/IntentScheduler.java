package com.walkmate.application.walkintent;

import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.walkintent.IntentStatus;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Runs the intent expiry sweep every 60 seconds.
 *
 * <p>Handles two rules:</p>
 * <ul>
 *   <li><b>GAP-14 / Note #2</b> — OPEN/MATCHING intent with start ≤ now + 5 min → EXPIRED.</li>
 *   <li><b>GAP-13 / I-5</b>     — OPEN/MATCHING intent whose window has fully passed → EXPIRED,
 *       cascading to any PENDING proposal and unlocking the partner's intent.</li>
 * </ul>
 *
 * Each intent is expired in its own isolated transaction. A failure on one intent
 * rolls back only that item; the sweep continues to the next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentScheduler {

    private static final Duration EXPIRY_BUFFER = Duration.ofMinutes(5);

    private final WalkIntentRepository    walkIntentRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final TransactionTemplate     transactionTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void handleExpiredIntents() {
        Instant now = Instant.now();
        log.debug("IntentScheduler: starting intent expiry sweep");

        // GAP-14: intents within 5 min of their scheduled start (window not yet ended)
        try {
            List<WalkIntent> aboutToStart = walkIntentRepository.findIntentsExpiringSoon(now, EXPIRY_BUFFER);
            for (WalkIntent intent : aboutToStart) {
                expireIntentSafely(intent);
            }
        } catch (Exception e) {
            log.error("IntentScheduler: pre-start expiry sweep failed unexpectedly", e);
        }

        // GAP-13: intents whose time window has fully passed
        try {
            List<WalkIntent> pastEnd = walkIntentRepository.findOverdueOpenIntents(now);
            for (WalkIntent intent : pastEnd) {
                expireIntentSafely(intent);
            }
        } catch (Exception e) {
            log.error("IntentScheduler: overdue expiry sweep failed unexpectedly", e);
        }
    }

    private void expireIntentSafely(WalkIntent intent) {
        try {
            transactionTemplate.execute(status -> {
                expireIntent(intent);
                return null;
            });
        } catch (Exception e) {
            log.error("IntentScheduler: failed to expire intent={}", intent.getId(), e);
        }
    }

    private void expireIntent(WalkIntent intent) {
        intent.expire();
        walkIntentRepository.save(intent);

        // I-5: cascade expiry to any PENDING proposal and unlock the partner's intent
        matchProposalRepository.findPendingByIntentId(intent.getId())
                .ifPresent(proposal -> {
                    proposal.expire();
                    matchProposalRepository.save(proposal);

                    String partnerIntentId = proposal.getIntentIdA().equals(intent.getId())
                            ? proposal.getIntentIdB() : proposal.getIntentIdA();
                    walkIntentRepository.findById(partnerIntentId)
                            .filter(p -> p.getStatus() == IntentStatus.MATCHING)
                            .ifPresent(partner -> {
                                partner.unlock();
                                walkIntentRepository.save(partner);
                            });
                });

        log.info("IntentScheduler: intent {} expired", intent.getId());
    }
}
