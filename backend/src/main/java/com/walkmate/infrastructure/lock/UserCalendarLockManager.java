package com.walkmate.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user calendar serialization via PostgreSQL transaction-scoped advisory locks.
 *
 * <p>Every writer that can reserve or release a user's calendar slot MUST acquire that
 * user's advisory lock first, inside the same transaction that performs the write. The
 * lock is released automatically when the transaction ends (xact-scoped), so no explicit
 * unlock is required.</p>
 *
 * <p>Canonical lock order (see {@code docs/implementation/matching-flow-decisions.md}):</p>
 * <ol>
 *   <li>user advisory locks (sorted by canonical UUID string)</li>
 *   <li>intent row locks (sorted by intent id)</li>
 *   <li>proposal row locks (sorted by proposal id)</li>
 *   <li>mutations</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class UserCalendarLockManager {

    /**
     * Namespace constant mixed into the advisory key so calendar locks cannot collide
     * with any other advisory-lock user in the same database. Key collisions between two
     * distinct users only serialize them (reduce throughput) — they never break safety.
     */
    private static final long NAMESPACE = 0x57_41_4C_4B_4D_41_54L; // "WALKMATE" (8 bytes)

    private final JdbcClient jdbcClient;

    /**
     * Acquires a transaction-scoped advisory lock for each distinct user, in a stable
     * sorted order. Repeated ids are de-duplicated.
     *
     * @param userIds raw user ids (strings). Malformed ids are rejected up-front.
     */
    public void lockUsers(Collection<String> userIds) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("UserCalendarLockManager requires an active transaction");
        }
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        for (String userId : normalizeAndSort(userIds)) {
            jdbcClient.sql("SELECT pg_advisory_xact_lock(:key)")
                    .param("key", advisoryKey(userId))
                    .query((rs, rowNum) -> 1)
                    .single();
        }
    }

    private Set<String> normalizeAndSort(Collection<String> userIds) {
        List<UUID> uuids = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("User id must not be blank");
            }
            uuids.add(UUID.fromString(userId));
        }
        uuids.sort(Comparator.comparing(UUID::toString));

        Set<String> result = new LinkedHashSet<>();
        for (UUID u : uuids) {
            result.add(u.toString());
        }
        return result;
    }

    private long advisoryKey(String userId) {
        UUID uuid = UUID.fromString(userId);
        long key = NAMESPACE;
        key = key * 31 + uuid.getMostSignificantBits();
        key = key * 31 + uuid.getLeastSignificantBits();
        return key;
    }
}
