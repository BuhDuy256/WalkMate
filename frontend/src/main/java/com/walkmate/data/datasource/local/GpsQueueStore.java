package com.walkmate.data.datasource.local;

import android.util.Log;

import com.walkmate.data.datasource.local.dao.GpsBatchDao;
import com.walkmate.data.datasource.local.dao.GpsStreamDao;
import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.GpsBatchEntity;
import com.walkmate.data.datasource.local.entity.GpsStreamEntity;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.domain.tracking.GpsBatch;
import com.walkmate.domain.tracking.GpsBatchState;
import com.walkmate.domain.tracking.GpsPayloadHasher;
import com.walkmate.domain.tracking.GpsSyncState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Transactional local store for the GPS v2 upload queue.
 *
 * <p>Everything that must be atomic lives here, not in the repository, because
 * Room's unit of consistency is {@link WalkMateDatabase#runInTransaction}. The
 * three invariants this class exists to protect:</p>
 *
 * <ol>
 *   <li><b>Gap-free sequences.</b> {@code nextSequence} is reserved and the row
 *       inserted in one transaction. Reading {@code MAX(sequence) + 1} in a
 *       separate transaction can hand the same number to two callers.</li>
 *   <li><b>Immutable batch payloads.</b> Sealing writes the batch row, computes
 *       the canonical hash and stamps {@code batchId} onto the points in a single
 *       transaction. A retry therefore rebuilds a byte-identical request, which
 *       is what makes a lost-ACK retry verifiable server-side.</li>
 *   <li><b>Verified ACKs only.</b> The batch state and its points move to
 *       synced/ACKED together, and only when the returned batch id, stream,
 *       range and hash all match the stored row.</li>
 * </ol>
 *
 * <p>All methods block; callers must be on a background thread.</p>
 */
public class GpsQueueStore {

    private static final String TAG = "GpsQueueStore";

    /** Batch size the backend accepts; mirrors {@code MAX_POINTS_PER_BATCH}. */
    public static final int MAX_POINTS_PER_BATCH = 50;

    /** Lease duration for an in-flight batch, so a crashed worker does not strand it. */
    public static final long DEFAULT_LEASE_MS = 60_000L;

    private final WalkMateDatabase db;
    private final RoutePointDao pointDao;
    private final GpsStreamDao streamDao;
    private final GpsBatchDao batchDao;

    public GpsQueueStore(WalkMateDatabase db) {
        this.db = db;
        this.pointDao = db.routePointDao();
        this.streamDao = db.gpsStreamDao();
        this.batchDao = db.gpsBatchDao();
    }

    // ── Stream lifecycle ──────────────────────────────────────────────────────

    /**
     * Returns the stream id for this {@code (sessionId, userId)}, minting and
     * persisting one on first use. The id is stable across process restarts,
     * which is what lets a retry after a crash stay on the same server stream.
     */
    public String ensureStream(String sessionId, String userId) {
        return db.runInTransaction(() -> {
            GpsStreamEntity existing = streamDao.find(sessionId, userId);
            if (existing != null && existing.streamId != null) {
                return existing.streamId;
            }

            long now = System.currentTimeMillis();
            String streamId = UUID.randomUUID().toString();

            if (existing == null) {
                GpsStreamEntity entity = new GpsStreamEntity();
                entity.sessionId = sessionId;
                entity.userId = userId;
                entity.streamId = streamId;
                entity.nextSequence = 1L;
                entity.syncState = GpsSyncState.OPEN;
                entity.createdAt = now;
                entity.updatedAt = now;
                streamDao.insertIfAbsent(entity);

                // Another writer may have inserted between the read and the insert.
                GpsStreamEntity stored = streamDao.find(sessionId, userId);
                return stored != null && stored.streamId != null ? stored.streamId : streamId;
            }

            // Row exists but was left without a stream id by an interrupted run.
            streamDao.setStreamId(sessionId, userId, streamId, now);
            return streamId;
        });
    }

    /** Last sequence handed out for this stream (0 when nothing was recorded). */
    public long lastAllocatedSequence(String sessionId, String userId) {
        return streamDao.lastAllocatedSequence(sessionId, userId);
    }

    // ── Accept a point ────────────────────────────────────────────────────────

    /**
     * Allocates the next sequence and persists the fix in one transaction.
     *
     * @return the assigned 1-based sequence
     */
    public long saveAcceptedPoint(String sessionId, String userId, String streamId,
                                  double lat, double lng, long recordedAt, float accuracy) {
        return db.runInTransaction(() -> {
            ensureStream(sessionId, userId);

            Long next = streamDao.currentNextSequence(sessionId, userId);
            if (next == null || next < 1L) {
                // Defensive: a half-migrated row must not silently restart at 1.
                throw new IllegalStateException(
                        "gps_stream row missing for " + sessionId + "/" + userId);
            }

            RoutePointEntity entity = new RoutePointEntity();
            entity.sessionId = sessionId;
            entity.userId = userId;
            entity.streamId = streamId;
            entity.sequence = next;
            entity.batchId = null;
            entity.lat = lat;
            entity.lng = lng;
            entity.timestamp = recordedAt;
            entity.accuracy = accuracy;
            entity.isSynced = false;

            pointDao.insertPoint(entity);
            streamDao.bumpNextSequence(sessionId, userId, System.currentTimeMillis());

            return next;
        });
    }

    // ── Seal a batch ──────────────────────────────────────────────────────────

    /**
     * Freezes up to {@code MAX_POINTS_PER_BATCH} unbatched points into a new
     * batch, computing the canonical hash from the frozen snapshot.
     *
     * @return the sealed batch, or {@code null} when nothing is pending
     */
    public GpsBatch sealNextBatch(String sessionId, String userId) {
        return db.runInTransaction(() -> {
            List<RoutePointEntity> candidates =
                    pointDao.getUnbatchedPoints(sessionId, userId, MAX_POINTS_PER_BATCH);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }

            // Sequence gaps would produce a range the server rejects; sealing only
            // ever takes a contiguous run starting at the first candidate.
            long seqStart = candidates.get(0).sequence;
            List<RoutePointEntity> run = new ArrayList<>(candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                RoutePointEntity e = candidates.get(i);
                if (e.sequence != seqStart + i) {
                    break;
                }
                run.add(e);
            }
            if (run.isEmpty()) {
                return null;
            }

            long seqEnd = run.get(run.size() - 1).sequence;
            String streamId = run.get(0).streamId;
            if (streamId == null) {
                streamId = ensureStream(sessionId, userId);
            }

            List<Long> ids = new ArrayList<>(run.size());
            List<GpsPayloadHasher.Point> canonical = new ArrayList<>(run.size());
            for (RoutePointEntity e : run) {
                ids.add(e.id);
                canonical.add(new GpsPayloadHasher.Point(
                        e.sequence, e.timestamp, e.lat, e.lng, e.accuracy));
            }

            String batchId = UUID.randomUUID().toString();
            String hash = GpsPayloadHasher.hash(
                    sessionId, userId, streamId, seqStart, seqEnd, canonical);

            long now = System.currentTimeMillis();
            GpsBatchEntity batch = new GpsBatchEntity();
            batch.batchId = batchId;
            batch.sessionId = sessionId;
            batch.userId = userId;
            batch.streamId = streamId;
            batch.seqStart = seqStart;
            batch.seqEnd = seqEnd;
            batch.payloadHash = hash;
            batch.state = GpsBatchState.PENDING;
            batch.retryCount = 0;
            batch.nextAttemptAt = now;
            batch.leaseUntil = 0L;
            batch.createdAt = now;

            batchDao.insert(batch);
            pointDao.assignBatch(ids, batchId);

            return new GpsBatch(batchId, sessionId, userId, streamId,
                    seqStart, seqEnd, hash, GpsBatchState.PENDING, 0, now, canonical);
        });
    }

    /** Seals every remaining point, one batch at a time. */
    public int sealAllPending(String sessionId, String userId) {
        int sealed = 0;
        // Bounded loop: each iteration consumes at least one point.
        for (int i = 0; i < 1_000; i++) {
            GpsBatch batch = sealNextBatch(sessionId, userId);
            if (batch == null) {
                break;
            }
            sealed++;
        }
        return sealed;
    }

    // ── Queue reads ───────────────────────────────────────────────────────────

    public int countUnbatched(String sessionId, String userId) {
        return pointDao.countUnbatched(sessionId, userId);
    }

    public int countOutstanding(String sessionId, String userId) {
        return batchDao.countOutstanding(sessionId, userId);
    }

    public boolean hasOutstanding(String sessionId, String userId) {
        return countOutstanding(sessionId, userId) > 0;
    }

    public List<GpsBatch> findDrainable(String sessionId, String userId) {
        List<GpsBatchEntity> rows = batchDao.findDrainable(sessionId, userId,
                System.currentTimeMillis());
        List<GpsBatch> out = new ArrayList<>(rows.size());
        for (GpsBatchEntity row : rows) {
            GpsBatch batch = hydrate(row);
            if (batch != null) {
                out.add(batch);
            }
        }
        return out;
    }

    public List<GpsBatch> findBlocked(String sessionId, String userId) {
        List<GpsBatchEntity> rows = batchDao.findBlocked(sessionId, userId);
        List<GpsBatch> out = new ArrayList<>(rows.size());
        for (GpsBatchEntity row : rows) {
            GpsBatch batch = hydrate(row);
            if (batch != null) {
                out.add(batch);
            }
        }
        return out;
    }

    public long highestAckedSequence(String sessionId, String userId) {
        return batchDao.highestAckedSeq(sessionId, userId);
    }

    /** Sessions owned by this user that still have unsent batches. */
    public List<String> sessionsWithOutstanding(String userId) {
        return batchDao.findSessionsWithOutstanding(userId);
    }

    /**
     * Rebuilds a batch from its frozen point rows. Returns null when the rows are
     * gone, in which case the batch cannot be resent and is left untouched.
     */
    private GpsBatch hydrate(GpsBatchEntity row) {
        List<RoutePointEntity> points = pointDao.getPointsOfBatch(row.batchId);
        if (points == null || points.isEmpty()) {
            Log.w(TAG, "Batch " + row.batchId + " has no point rows — skipping");
            return null;
        }
        List<GpsPayloadHasher.Point> canonical = new ArrayList<>(points.size());
        for (RoutePointEntity e : points) {
            canonical.add(new GpsPayloadHasher.Point(
                    e.sequence, e.timestamp, e.lat, e.lng, e.accuracy));
        }
        return new GpsBatch(row.batchId, row.sessionId, row.userId, row.streamId,
                row.seqStart, row.seqEnd, row.payloadHash, row.state,
                row.retryCount, row.nextAttemptAt, canonical);
    }

    // ── Queue transitions ─────────────────────────────────────────────────────

    /** Takes the lease; {@code false} means another worker already owns it. */
    public boolean claim(String batchId) {
        long now = System.currentTimeMillis();
        return batchDao.claim(batchId, now, now + DEFAULT_LEASE_MS) > 0;
    }

    /**
     * Records a verified ACK. The predicate on {@code markAcked} is what makes
     * this safe: a response for a different stream/range/hash cannot promote the
     * row, and its points are only marked synced after that succeeds.
     *
     * @return true when the stored batch matched the ACK
     */
    public boolean ackVerified(GpsBatch batch, String ackStreamId, long ackSeqStart,
                               long ackSeqEnd, String ackPayloadHash) {
        return db.runInTransaction(() -> {
            boolean matches = batch.getStreamId().equals(ackStreamId)
                    && batch.getSeqStart() == ackSeqStart
                    && batch.getSeqEnd() == ackSeqEnd
                    && batch.getPayloadHash().equalsIgnoreCase(ackPayloadHash);
            if (!matches) {
                Log.w(TAG, "ACK did not match the stored batch " + batch.getBatchId());
                return false;
            }
            int updated = batchDao.markAcked(batch.getBatchId(), ackStreamId,
                    ackSeqStart, ackSeqEnd, batch.getPayloadHash(), System.currentTimeMillis());
            if (updated == 0) {
                return false;
            }
            pointDao.markBatchSynced(batch.getBatchId());
            return true;
        });
    }

    /** Returns a batch to the queue with exponential backoff. */
    public void release(GpsBatch batch) {
        long backoffMs = Math.min(15L * 60L * 1000L,
                5_000L * (1L << Math.min(batch.getRetryCount(), 8)));
        batchDao.release(batch.getBatchId(), System.currentTimeMillis() + backoffMs);
    }

    /** Parks a batch the server permanently refused. Nothing is deleted. */
    public void block(String batchId, String reason) {
        batchDao.block(batchId, reason);
    }

    // ── Seal / completion intent ──────────────────────────────────────────────

    /**
     * Records the final sequence once the walk ends. Idempotent: a
     * second call keeps the highest value so a resumed process cannot shrink it.
     */
    public void persistFinalSequence(String sessionId, String userId,
                                     long finalSequence, Long recordedEndedAt) {
        db.runInTransaction(() -> {
            ensureStream(sessionId, userId);
            GpsStreamEntity stream = streamDao.find(sessionId, userId);
            long chosen = stream != null && stream.finalSequence != null
                    ? Math.max(stream.finalSequence, finalSequence)
                    : finalSequence;
            streamDao.setSealed(sessionId, userId, chosen, recordedEndedAt,
                    GpsSyncState.SEALED, System.currentTimeMillis());
            return null;
        });
    }

    public void setSyncState(String sessionId, String userId, String syncState) {
        streamDao.setSyncState(sessionId, userId, syncState, System.currentTimeMillis());
    }

    public String syncState(String sessionId, String userId) {
        GpsStreamEntity stream = streamDao.find(sessionId, userId);
        return stream != null ? stream.syncState : null;
    }

    public Long finalSequence(String sessionId, String userId) {
        GpsStreamEntity stream = streamDao.find(sessionId, userId);
        return stream != null ? stream.finalSequence : null;
    }

    // ── Logout cleanup ────────────────────────────────────────────────────────

    public void clearForUser(String userId) {
        db.runInTransaction(() -> {
            batchDao.deleteByUserId(userId);
            streamDao.deleteByUserId(userId);
            pointDao.deleteByUserId(userId);
            return null;
        });
    }
}
