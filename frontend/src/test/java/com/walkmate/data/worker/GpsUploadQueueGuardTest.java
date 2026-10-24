package com.walkmate.data.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.walkmate.data.datasource.local.GpsQueueStore;
import com.walkmate.data.datasource.local.WalkMateDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Guards that stop one account's GPS route from being handled under another.
 *
 * <p>Plan T15: a queue left behind by user A must not be uploaded while user B is
 * signed in, and logout must cancel work enqueued with A's credentials — otherwise
 * the route is either attributed to B or uploaded with a token it does not belong
 * to.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class GpsUploadQueueGuardTest {

    private static final String USER_A  = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String USER_B  = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String SESSION = "11111111-1111-1111-1111-111111111111";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(new SynchronousExecutor())
                .build();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration);
    }

    // ── Identity rule ─────────────────────────────────────────────────────────

    @Test
    public void theQueueOwnerMustBeTheSignedInUser() {
        assertTrue(GpsUploadWorker.mayUpload(USER_A, USER_A));
        assertFalse("another account must never drain this queue",
                GpsUploadWorker.mayUpload(USER_B, USER_A));
    }

    @Test
    public void anUnknownSignedInUserIsNeverAllowed() {
        assertFalse("signed out must not upload anyone's route",
                GpsUploadWorker.mayUpload(null, USER_A));
        assertFalse(GpsUploadWorker.mayUpload("", USER_A));
        assertFalse(GpsUploadWorker.mayUpload(USER_A, null));
        assertFalse(GpsUploadWorker.mayUpload(USER_A, ""));
    }

    // ── Logout cancellation ───────────────────────────────────────────────────

    @Test
    public void logoutCancelsThatUsersUploadWork() throws Exception {
        GpsSyncScheduler scheduler = new GpsSyncScheduler(context);
        scheduler.requestDrain(USER_A, SESSION, false);

        WorkManager wm = WorkManager.getInstance(context);
        String uniqueName = GpsSyncScheduler.workName(USER_A, SESSION);

        List<WorkInfo> before = wm.getWorkInfosForUniqueWork(uniqueName).get();
        assertEquals("the drain was enqueued", 1, before.size());
        assertFalse("and it is not finished yet", before.get(0).getState().isFinished());

        scheduler.cancelForUser(USER_A);

        List<WorkInfo> after = wm.getWorkInfosForUniqueWork(uniqueName).get();
        for (WorkInfo info : after) {
            assertTrue("cancelled work must not remain runnable",
                    info.getState().isFinished());
        }
    }

    @Test
    public void cancellingOneUserLeavesAnotherUsersWorkAlone() throws Exception {
        GpsSyncScheduler scheduler = new GpsSyncScheduler(context);
        scheduler.requestDrain(USER_A, SESSION, false);
        scheduler.requestDrain(USER_B, SESSION, false);

        scheduler.cancelForUser(USER_A);

        WorkManager wm = WorkManager.getInstance(context);
        List<WorkInfo> forB = wm.getWorkInfosForUniqueWork(
                GpsSyncScheduler.workName(USER_B, SESSION)).get();

        assertEquals(1, forB.size());
        assertFalse("cancelling A must not cancel B's pending upload",
                forB.get(0).getState().isFinished());
    }

    // ── Scheduling is per (user, session) ─────────────────────────────────────

    @Test
    public void uniqueWorkNamesAreScopedPerUserAndSession() {
        assertFalse(GpsSyncScheduler.workName(USER_A, SESSION)
                .equals(GpsSyncScheduler.workName(USER_B, SESSION)));
        assertFalse(GpsSyncScheduler.workName(USER_A, SESSION)
                .equals(GpsSyncScheduler.workName(USER_A, "other-session")));
        assertFalse(GpsSyncScheduler.tagForUser(USER_A)
                .equals(GpsSyncScheduler.tagForUser(USER_B)));
    }
}
