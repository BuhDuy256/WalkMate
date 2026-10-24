package com.walkmate.application.tracking;

import com.walkmate.domain.tracking.GpsStream;
import com.walkmate.domain.tracking.TrackingContractException;
import com.walkmate.domain.tracking.TrackingErrorCode;
import com.walkmate.domain.tracking.TrackingStreamRepository;
import com.walkmate.domain.tracking.TrackingStreamSyncState;
import com.walkmate.presentation.dto.request.tracking.SealStreamRequest;
import com.walkmate.presentation.dto.response.tracking.SealStreamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * v2 stream seal: the client declares its final sequence number (and, when
 * it knows one, the recorded end of the walk). The server computes the
 * upload deadline ({@code now + LATE_UPLOAD_WINDOW}), moves the stream to
 * SEALED and writes the early terminal state if coverage is already
 * complete or the deadline cannot be reached.
 *
 * <p>Retries are idempotent: re-sealing with the same or a lower final
 * sequence and the same values is a no-op; a higher final sequence extends
 * the range (the seal deadline does NOT restart — it is first-seal based +
 * we keep the earlier deadline so a late client is never silently extended
 * by a second device that resends with a higher sequence).</p>
 */
@Service
@RequiredArgsConstructor
public class TrackingSealService {

    private static final Duration LATE_UPLOAD_WINDOW = Duration.ofDays(7);

    private final TrackingStreamRepository streamRepository;

    /**
     * Idempotent: re-sealing with the same final sequence is a no-op; a
     * higher final sequence extends the range.
     */
    @Transactional
    public SealStreamResponse seal(String streamId, String callerId, SealStreamRequest request) {
        GpsStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new TrackingContractException(
                        TrackingErrorCode.TRACKING_STREAM_CONFLICT,
                        "stream " + streamId + " is not known to this server"));
        if (!stream.userId().equals(callerId)) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_CONFLICT,
                    "stream " + streamId + " belongs to another user");
        }

        Long serverFinal = stream.expectedFinalSequence();
        Long clientFinal = request.finalSequence();

        if (serverFinal != null && clientFinal < serverFinal) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_CONFLICT,
                    "stream is sealed for final sequence " + serverFinal
                            + "; a lower value is not accepted");
        }

        Instant recordedEndedAt = request.recordedEndedAt() != null
                ? Instant.ofEpochMilli(request.recordedEndedAt()) : null;

        if (stream.syncState() == TrackingStreamSyncState.EXPIRED) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_EXPIRED,
                    "stream " + streamId + " is already past its upload deadline");
        }

        streamRepository.sealStream(streamId,
                clientFinal != null ? clientFinal : (serverFinal != null ? serverFinal : 0L),
                recordedEndedAt, LATE_UPLOAD_WINDOW);

        // Re-evaluate terminal state so a full-coverage stream seals straight
        // to COMPLETE in one round-trip.
        TrackingStreamSyncState after = streamRepository.evaluateCompletion(streamId);
        GpsStream afterSnapshot = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalStateException("Stream " + streamId + " vanished"));

        return new SealStreamResponse(
                streamId,
                after.name(),
                afterSnapshot.expectedFinalSequence(),
                afterSnapshot.uploadDeadlineAt() != null
                        ? afterSnapshot.uploadDeadlineAt().toString() : null);
    }

    /**
     * Idempotent housekeeping for a sealed stream whose late-upload window has
     * elapsed without reaching full coverage: promotes it to {@code EXPIRED}.
     *
     * <p>This runs in its own transaction on purpose. Inside
     * {@code pushBatch} the same evaluation happens before the batch is
     * rejected, but that transaction rolls back — so the durable transition is
     * made here, after the fact. A stream that is still within its deadline, or
     * already terminal, is left untouched.</p>
     */
    @Transactional
    public SealStreamResponse expire(String streamId, String callerId) {
        GpsStream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> new TrackingContractException(
                        TrackingErrorCode.TRACKING_STREAM_CONFLICT,
                        "stream " + streamId + " is not known to this server"));
        if (!stream.userId().equals(callerId)) {
            throw new TrackingContractException(TrackingErrorCode.TRACKING_STREAM_CONFLICT,
                    "stream " + streamId + " belongs to another user");
        }

        TrackingStreamSyncState after = streamRepository.evaluateCompletion(streamId);
        GpsStream snapshot = streamRepository.findById(streamId)
                .orElseThrow(() -> new IllegalStateException("Stream " + streamId + " vanished"));

        return new SealStreamResponse(
                streamId,
                after.name(),
                snapshot.expectedFinalSequence(),
                snapshot.uploadDeadlineAt() != null
                        ? snapshot.uploadDeadlineAt().toString() : null);
    }
}
