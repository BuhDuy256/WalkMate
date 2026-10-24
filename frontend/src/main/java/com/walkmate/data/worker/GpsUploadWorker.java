package com.walkmate.data.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.walkmate.WalkMateApplication;
import com.walkmate.data.repository.GpsQueueDrainer;

/**
 * Uploads the persisted GPS queue for one {@code (sessionId, userId)} pair.
 *
 * <p>WorkManager is used for <em>upload only</em>: it retries across process
 * death and network loss, but it does not and cannot keep location callbacks
 * alive. The durable part is the Room queue, not the worker — if this worker
 * never runs again, the points are still on disk and will drain on the next
 * app start or network change.</p>
 *
 * <p>The worker reports {@code Result.retry()} only for transient failures; a
 * permanent server refusal parks the batch as BLOCKED and does not burn retry
 * budget forever.</p>
 */
public class GpsUploadWorker extends Worker {

    private static final String TAG = "GpsUploadWorker";

    public static final String KEY_SESSION_ID = "session_id";
    public static final String KEY_USER_ID    = "user_id";
    /** When true the worker also seals the stream once its backlog is clear. */
    public static final String KEY_SEAL       = "seal";

    /** Cap per run so a huge backlog cannot keep one worker alive indefinitely. */
    private static final int MAX_DRAIN_ROUNDS = 40;

    public GpsUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sessionId = getInputData().getString(KEY_SESSION_ID);
        String userId    = getInputData().getString(KEY_USER_ID);
        boolean seal     = getInputData().getBoolean(KEY_SEAL, false);

        if (sessionId == null || sessionId.isEmpty()
                || userId == null || userId.isEmpty()) {
            Log.w(TAG, "Missing session/user — nothing to upload");
            return Result.failure();
        }

        Context app = getApplicationContext();
        if (!(app instanceof WalkMateApplication)) {
            Log.w(TAG, "Unexpected application class — aborting");
            return Result.failure();
        }
        WalkMateApplication application = (WalkMateApplication) app;

        // The queue worker must never upload one account's route under another
        // account's credentials (e.g. logout then login as someone else).
        String currentUserId = application.getSessionManager().getUserId();
        if (!mayUpload(currentUserId, userId)) {
            Log.w(TAG, "Signed-in user " + currentUserId
                    + " does not own the queued batch (" + userId + ") — stopping");
            return Result.success();
        }

        GpsQueueDrainer drainer = application.getGpsQueueDrainer();
        if (drainer == null) {
            return Result.retry();
        }

        int uploadedThisRun = 0;
        boolean anyTransientFailure = false;

        for (int i = 0; i < MAX_DRAIN_ROUNDS; i++) {
            GpsQueueDrainer.DrainResult result = drainer.drain(sessionId, userId, true);
            uploadedThisRun += result.uploaded;

            if (result.terminal) {
                Log.w(TAG, "Stream is terminal — no further uploads");
                break;
            }
            if (result.remaining == 0) {
                break;
            }
            if (result.uploaded == 0) {
                // Nothing moved: either everything is in backoff or the network is
                // down. Let WorkManager apply its own backoff.
                anyTransientFailure = true;
                break;
            }
        }

        Log.d(TAG, "Run finished — uploaded=" + uploadedThisRun
                + " remaining=" + application.getGpsQueueStore().countOutstanding(sessionId, userId));

        if (seal && !anyTransientFailure) {
            drainer.seal(sessionId, userId);
        }

        return anyTransientFailure ? Result.retry() : Result.success();
    }

    /**
     * Whether the queue owned by {@code queuedUserId} may be uploaded by the
     * currently signed-in user.
     *
     * <p>Extracted so the rule is explicit and testable: a route recorded by one
     * account must never be uploaded with another account's credentials, which is
     * what would happen if a logout left work queued and a different user then
     * signed in on the same device. An unknown signed-in user is treated as
     * "not allowed" — there is no safe default here.</p>
     */
    static boolean mayUpload(String signedInUserId, String queuedUserId) {
        return signedInUserId != null
                && !signedInUserId.isEmpty()
                && signedInUserId.equals(queuedUserId);
    }
}
