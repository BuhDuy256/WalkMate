package com.walkmate.gps;

import com.walkmate.gps.support.GpsFixtures;
import com.walkmate.gps.support.TestBatch;
import com.walkmate.presentation.dto.response.session.SessionRouteResponse;
import com.walkmate.presentation.dto.response.tracking.PartnerPathResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read half of out-of-order sequencing (plan §4.5).
 *
 * <p>Accepting an out-of-order batch is only half the guarantee: if history is
 * still assembled by {@code chunk_index} (arrival) the route is drawn in the
 * wrong order, and if the partner overlay appends blindly a late batch produces a
 * zig-zag. These tests assert the API returns data that lets a client place each
 * chunk by source sequence.</p>
 *
 * <p>Complements {@code GpsV2SequenceOrderingTest}, which proves the write half
 * (T09/T10) at the storage layer.</p>
 */
@DisplayName("GPS v2 — read paths honour source sequence (T09/T10 read half)")
class GpsV2ReadPathTest extends GpsV2TestSupport {

    /** Arrival order deliberately reversed: 101–150 first, then 1–50, then 51–100. */
    private void uploadOutOfOrder() {
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 101, recordedAtBase + 100_000).request(), userA);
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase).request(), userA);
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 51, recordedAtBase + 50_000).request(), userA);
    }

    @Test
    @DisplayName("T09 history is ordered by sequence, not by arrival — route reads 1..150")
    void t09_historyRouteIsOrderedBySequence() {
        uploadOutOfOrder();
        GpsFixtures.setSessionStatus(jdbc, sessionId, "COMPLETED");

        // Sanity: the raw storage really is in a different order than the route.
        List<String> byArrival = GpsFixtures.polylinesByArrival(jdbc, sessionId, userA);
        List<String> bySequence = GpsFixtures.polylinesBySequence(jdbc, sessionId, userA);
        assertThat(byArrival).hasSize(3);
        assertThat(byArrival)
                .as("arrival order must differ from route order, or this test proves nothing")
                .isNotEqualTo(bySequence);

        SessionRouteResponse route = instance.queryService().getSessionRoute(sessionId, userA);

        assertThat(route.userAPolylines())
                .as("route must be assembled by source sequence")
                .containsExactlyElementsOf(bySequence);

        // 101–150 arrived first but must render last.
        assertThat(route.userAPolylines().get(0))
                .isEqualTo(bySequence.get(0))
                .isNotEqualTo(byArrival.get(0));

        // The partner uploaded nothing, so their path is empty rather than fabricated.
        assertThat(route.userBPolylines()).isEmpty();
    }

    @Test
    @DisplayName("T10 a partial route is returned as-is — no stitching across the missing prefix")
    void t10_partialRouteIsNotFabricated() {
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 101, recordedAtBase + 100_000).request(), userA);
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 151, recordedAtBase + 150_000).request(), userA);
        GpsFixtures.setSessionStatus(jdbc, sessionId, "COMPLETED");

        SessionRouteResponse route = instance.queryService().getSessionRoute(sessionId, userA);

        // Two chunks, both real; the missing 1–100 is simply absent.
        assertThat(route.userAPolylines()).hasSize(2);
        assertThat(route.userAPolylines())
                .containsExactlyElementsOf(GpsFixtures.polylinesBySequence(jdbc, sessionId, userA));
    }

    @Test
    @DisplayName("Partner live poll reports each chunk's sequence range so a late batch can be slotted in")
    void partnerPollCarriesSequenceRange() {
        // User A walks; user B watches via the partner overlay.
        uploadOutOfOrder();

        PartnerPathResponse live = instance.queryService().getPartnerPath(sessionId, userB, -1);

        assertThat(live.partnerStatus()).isEqualTo("ACTIVE");
        assertThat(live.chunks()).hasSize(3);

        // The chunks arrive in arrival order (that is the pagination contract)…
        assertThat(live.chunks()).extracting(PartnerPathResponse.PartnerChunk::chunkIndex)
                .containsExactly(0, 1, 2);

        // …but each one carries the range the client needs to reorder them,
        // and the union of ranges covers 1..150 exactly once.
        List<Long> starts = live.chunks().stream()
                .map(PartnerPathResponse.PartnerChunk::seqStart)
                .sorted()
                .toList();
        assertThat(starts).containsExactly(1L, 51L, 101L);

        for (PartnerPathResponse.PartnerChunk chunk : live.chunks()) {
            assertThat(chunk.streamId()).isEqualTo(streamA);
            assertThat(chunk.seqEnd() - chunk.seqStart() + 1).isEqualTo(50L);
        }

        // The cursor is the arrival index, which is what the client passes back.
        assertThat(live.lastChunkCreatedAtMs()).isPositive();
    }

    @Test
    @DisplayName("Partner poll keeps the arrival cursor — an incremental poll returns only newer chunks")
    void partnerPollPaginationIsStable() {
        uploadOutOfOrder();

        PartnerPathResponse firstPage = instance.queryService().getPartnerPath(sessionId, userB, 0);
        assertThat(firstPage.chunks()).extracting(PartnerPathResponse.PartnerChunk::chunkIndex)
                .as("afterChunkIndex=0 returns strictly later arrivals")
                .containsExactly(1, 2);

        PartnerPathResponse none = instance.queryService().getPartnerPath(sessionId, userB, 2);
        assertThat(none.chunks()).isEmpty();
        assertThat(none.partnerStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("A session holding legacy chunks keeps arrival ordering instead of inventing route positions")
    void mixedLegacySessionFallsBackToArrivalOrder() {
        // A pre-upgrade chunk exists for this participant: it has no sequence, so
        // there is no honest way to interleave it with the v2 rows.
        //
        // The arrival index is deliberately past the v2 cursor: chunk_index 0
        // belongs to the v2 stream, and reusing it is refused by the per-user
        // unique constraint (that refusal is itself covered by T12).
        GpsFixtures.seedLegacyChunkAt(jdbc, sessionId, userA, 5, "legacy-polyline", 5);

        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 101, recordedAtBase + 100_000).request(), userA);
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase).request(), userA);
        GpsFixtures.setSessionStatus(jdbc, sessionId, "COMPLETED");

        SessionRouteResponse route = instance.queryService().getSessionRoute(sessionId, userA);

        assertThat(route.userAPolylines())
                .as("mixed legacy session must not be reordered as if it were fully v2")
                .containsExactlyElementsOf(GpsFixtures.polylinesByArrival(jdbc, sessionId, userA));

        // The legacy row is still returned, not dropped.
        assertThat(route.userAPolylines()).contains("legacy-polyline");
    }

    @Test
    @DisplayName("Partner poll exposes null range for legacy chunks so the client can fall back")
    void partnerPollMarksLegacyChunksWithoutRange() {
        GpsFixtures.seedLegacyChunkAt(jdbc, sessionId, userA, 5, "legacy-polyline", 5);
        instance.commandService().pushBatch(
                TestBatch.fifty(sessionId, userA, streamA, 1, recordedAtBase).request(), userA);

        PartnerPathResponse live = instance.queryService().getPartnerPath(sessionId, userB, -1);

        assertThat(live.chunks()).hasSize(2);

        List<PartnerPathResponse.PartnerChunk> legacyRows = live.chunks().stream()
                .filter(c -> c.streamId() == null).toList();
        assertThat(legacyRows).hasSize(1);
        PartnerPathResponse.PartnerChunk legacy = legacyRows.get(0);
        assertThat(legacy.chunkIndex()).isEqualTo(5);
        assertThat(legacy.seqStart()).isNull();
        assertThat(legacy.seqEnd()).isNull();

        List<PartnerPathResponse.PartnerChunk> v2Rows = live.chunks().stream()
                .filter(c -> c.streamId() != null).toList();
        assertThat(v2Rows).hasSize(1);
        PartnerPathResponse.PartnerChunk v2 = v2Rows.get(0);
        assertThat(v2.streamId()).isEqualTo(streamA);
        assertThat(v2.seqStart()).isEqualTo(1L);
        assertThat(v2.seqEnd()).isEqualTo(50L);
    }
}
