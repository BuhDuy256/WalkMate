package com.walkmate.data.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules drain passes for the GPS upload queue.
 *
 * <p>Design notes that matter for correctness:</p>
 * <ul>
 *   <li><b>One-time work, not periodic.</b> The 30 s in-walk cadence cannot be
 *       expressed as periodic work — WorkManager's minimum periodic interval is
 *       15 minutes, so a periodic request would silently be 30× too slow.
 *       One-shot work is re-enqueued whenever there is something to upload.</li>
 *   <li><b>Unique work per (user, session).</b> A stable name means frequent
 *       triggers collapse into one run instead of stacking duplicate uploaders.
 *       The local lease plus server idempotency cover any residual overlap.</li>
 *   <li><b>Network constraint.</b> WorkManager waits for connectivity instead of
 *       letting each attempt fail and burn backoff.</li>
 * </ul>
 */
public class GpsSyncScheduler {

    private static final String TAG = "GpsSyncScheduler";

    private static final long BACKOFF_SECONDS = 30L;

    private final Context appContext;

    public GpsSyncScheduler(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static String workName(@NonNull String userId, @NonNull String sessionId) {
        return "gps-upload:" + userId + ":" + sessionId;
    }

    public static String tagForUser(@NonNull String userId) {
        return "gps-upload-user:" + userId;
    }

    /**
     * Requests a drain pass for this session.
     *
     * @param sealAfterDrain true when the walk has ended; the worker seals the
     *                       stream once the backlog is clear
     */
    public void requestDrain(@NonNull String userId, @NonNull String sessionId,
                             boolean sealAfterDrain) {
        if (userId.isEmpty() || sessionId.isEmpty()) {
            return;
        }
        Data input = new Data.Builder()
                .putString(GpsUploadWorker.KEY_SESSION_ID, sessionId)
                .putString(GpsUploadWorker.KEY_USER_ID, userId)
                .putBoolean(GpsUploadWorker.KEY_SEAL, sealAfterDrain)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(GpsUploadWorker.class)
                .setInputData(input)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,
                        BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(tagForUser(userId))
                .build();

        // A routine trigger must not cancel a drain that is already in progress —
        // cancelling mid-upload would leave the batch leased and delay the retry.
        // A seal request is appended instead, so it runs after the current chain
        // rather than replacing it.
        ExistingWorkPolicy policy = sealAfterDrain
                ? ExistingWorkPolicy.APPEND_OR_REPLACE
                : ExistingWorkPolicy.KEEP;

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                workName(userId, sessionId), policy, request);
        Log.d(TAG, "Drain requested for " + sessionId + " (seal=" + sealAfterDrain
                + ", policy=" + policy + ")");
    }

    /** Cancels pending upload work for a session (e.g. it was cancelled). */
    public void cancel(@NonNull String userId, @NonNull String sessionId) {
        WorkManager.getInstance(appContext)
                .cancelUniqueWork(workName(userId, sessionId));
    }

    /**
     * Cancels every upload owned by {@code userId}.
     *
     * <p>Called on logout before the session token is cleared: work enqueued with
     * the old credentials must not run afterwards, and one user's pending route
     * must never be uploaded under the next account.</p>
     */
    public void cancelForUser(@NonNull String userId) {
        WorkManager.getInstance(appContext).cancelAllWorkByTag(tagForUser(userId));
        Log.d(TAG, "Cancelled all GPS upload work for user " + userId);
    }
}
