package com.walkmate.domain.tracking;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain result returned by {@link TrackingRepository#fetchPartnerPath}.
 *
 * <p>The partner route is no longer a flat list of "new points". Under v2 a
 * batch can arrive out of order — the partner may upload 101–150 before 1–50 —
 * so appending each poll's points to the end of a list silently produced a
 * zig-zagging route. Each chunk now carries the sequence range it covers, and
 * callers re-order by {@code seqStart} instead of by arrival.</p>
 *
 * <p>Legacy (v1) chunks have no sequence range; they are exposed separately in
 * {@link #getLegacyPoints()} so a caller can decide to append them in arrival
 * order rather than pretend they are positionally meaningful.</p>
 */
public class PartnerPathResult {

    /** One decoded chunk with its route-position identity. */
    public static class Chunk {
        private final int chunkIndex;
        private final String streamId;
        private final long seqStart;
        private final long seqEnd;
        private final List<LatLng> points;

        public Chunk(int chunkIndex, String streamId, long seqStart, long seqEnd,
                     List<LatLng> points) {
            this.chunkIndex = chunkIndex;
            this.streamId = streamId;
            this.seqStart = seqStart;
            this.seqEnd = seqEnd;
            this.points = points != null
                    ? Collections.unmodifiableList(points) : Collections.<LatLng>emptyList();
        }

        public int getChunkIndex()      { return chunkIndex; }
        public String getStreamId()     { return streamId; }
        public long getSeqStart()       { return seqStart; }
        public long getSeqEnd()         { return seqEnd; }
        public List<LatLng> getPoints() { return points; }
    }

    private final List<LatLng> legacyPoints;
    private final List<Chunk> chunks;
    private final int lastChunkIndex;
    private final long lastChunkCreatedAtMs;
    private final String partnerStatus;

    public PartnerPathResult(List<LatLng> legacyPoints, List<Chunk> chunks,
                             int lastChunkIndex, long lastChunkCreatedAtMs,
                             String partnerStatus) {
        this.legacyPoints = legacyPoints != null
                ? Collections.unmodifiableList(legacyPoints) : Collections.<LatLng>emptyList();
        this.chunks = chunks != null
                ? Collections.unmodifiableList(chunks) : Collections.<Chunk>emptyList();
        this.lastChunkIndex = lastChunkIndex;
        this.lastChunkCreatedAtMs = lastChunkCreatedAtMs;
        this.partnerStatus = partnerStatus != null ? partnerStatus : "PENDING";
    }

    /** Legacy v1 chunks, which carry no sequence identity. */
    public List<LatLng> getLegacyPoints()  { return legacyPoints; }

    /** v2 chunks, each carrying the inclusive sequence range it covers. */
    public List<Chunk> getChunks()         { return chunks; }

    public int getLastChunkIndex()         { return lastChunkIndex; }
    public long getLastChunkCreatedAtMs()  { return lastChunkCreatedAtMs; }
    public String getPartnerStatus()       { return partnerStatus; }

    /**
     * All decoded points in <em>arrival</em> order across both formats. Convenience
     * only — route rendering should use {@link #getChunks()} so out-of-order
     * batches are placed correctly.
     */
    public List<LatLng> getNewPoints() {
        List<LatLng> all = new ArrayList<>();
        all.addAll(legacyPoints);
        for (Chunk c : chunks) {
            all.addAll(c.getPoints());
        }
        return all;
    }

    public boolean isEmpty() {
        return legacyPoints.isEmpty() && chunks.isEmpty();
    }
}
