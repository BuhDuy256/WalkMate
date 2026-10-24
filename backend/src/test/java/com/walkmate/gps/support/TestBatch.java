package com.walkmate.gps.support;

import com.walkmate.domain.tracking.GpsPayloadEncoder;
import com.walkmate.presentation.dto.request.tracking.TrackingBatchRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds {@link TrackingBatchRequest} exactly the way the Android client
 * would: device-minted batch id, contiguous 1-based sequences, epoch-millis
 * {@code recorded_at} (fix time), and the canonical SHA-256 computed with
 * the same {@link GpsPayloadEncoder} the server uses — so the pair proves
 * cross-language byte-level agreement, not a self-referential check.
 */
public final class TestBatch {

    private final TrackingBatchRequest request;

    private TestBatch(TrackingBatchRequest request) {
        this.request = request;
    }

    /**
     * Builds a valid v2 batch covering sequence {@code [from, from+count-1]}.
     *
     * @param fixedBatchId  when non-null, uses that batch id (replay tests)
     *                      instead of a fresh UUID
     */
    public static TestBatch of(String sessionId, String userId, String streamId,
                               long from, int count, long baseRecordedAtMs,
                               String fixedBatchId) {
        List<TrackingBatchRequest.V2Point> points = new ArrayList<>(count);
        List<GpsPayloadEncoder.Point> canonical = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long seq = from + i;
            // A deterministic pseudo-walk: advance slowly on lat, keep lng.
            double lat = 10.0 + (seq * 0.001);
            double lng = 106.0 + (seq * 0.0005);
            float accuracy = 5.0f + (i % 10) * 0.1f;
            points.add(new TrackingBatchRequest.V2Point(
                    seq, baseRecordedAtMs + i * 1000L, lat, lng, accuracy));
            canonical.add(new GpsPayloadEncoder.Point(
                    seq, baseRecordedAtMs + i * 1000L, lat, lng, accuracy));
        }

        String batchId = fixedBatchId != null ? fixedBatchId : UUID.randomUUID().toString();
        String hash = GpsPayloadEncoder.hash(sessionId, userId, streamId,
                from, from + count - 1, canonical);

        return new TestBatch(new TrackingBatchRequest(
                sessionId, streamId, batchId,
                from, from + count - 1, hash, points));
    }

    /** A 50-point batch — the default production size. */
    public static TestBatch fifty(String sessionId, String userId, String streamId,
                                  long from, long baseRecordedAtMs) {
        return of(sessionId, userId, streamId, from, 50, baseRecordedAtMs, null);
    }

    public TrackingBatchRequest request() {
        return request;
    }

    public String batchId() {
        return request.batchId();
    }

    /** Returns a copy with one point's coordinates mutated — the hash is left stale on purpose. */
    public TestBatch withPointMutated() {
        return mutated(0.5);
    }

    /** Returns a copy with one point's coordinates mutated — for range-overlap-style probes. */
    public TestBatch mutated(double latDelta) {
        List<TrackingBatchRequest.V2Point> points = new ArrayList<>(request.points().size());
        boolean swapped = false;
        for (TrackingBatchRequest.V2Point p : request.points()) {
            if (!swapped) {
                points.add(new TrackingBatchRequest.V2Point(
                        p.sequence(), p.recordedAt(), p.lat() + latDelta, p.lng(), p.accuracy()));
                swapped = true;
            } else {
                points.add(p);
            }
        }
        return new TestBatch(new TrackingBatchRequest(
                request.sessionId(), request.streamId(), request.batchId(),
                request.seqStart(), request.seqEnd(), request.payloadHash(), points));
    }

    /**
     * Recomputes {@code payload_hash} over the current payload — a payload swap
     * that satisfies the hash gate, so the batch-id reuse check is what rejects
     * it.
     */
    public TestBatch withRecomputedHash(String sessionId, String userId, String streamId) {
        List<GpsPayloadEncoder.Point> canonical = new ArrayList<>();
        for (TrackingBatchRequest.V2Point p : request.points()) {
            canonical.add(new GpsPayloadEncoder.Point(
                    p.sequence(), p.recordedAt(), p.lat(), p.lng(), p.accuracy()));
        }
        String hash = GpsPayloadEncoder.hash(sessionId, userId, streamId,
                request.seqStart(), request.seqEnd(), canonical);
        return new TestBatch(new TrackingBatchRequest(
                request.sessionId(), request.streamId(), request.batchId(),
                request.seqStart(), request.seqEnd(), hash, request.points()));
    }
}
