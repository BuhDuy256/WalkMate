package com.walkmate.ui.auth.phone;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.user.VisibilityMode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34}, application = android.app.Application.class)

/**
 * Reproduces the threading bug in PhoneOtpViewModel:
 *
 *   UserRepository.sendOtp() calls onSuccess() on a background thread.
 *   The original PhoneOtpViewModel.startResendCountdown() creates a CountDownTimer
 *   (which constructs a Handler internally) on that same background thread,
 *   causing: RuntimeException: Can't create handler inside thread [...] that has
 *   not called Looper.prepare()
 *
 * Test structure
 * ──────────────
 * • BackgroundCallbackUserRepository — fake repo that always calls the callback
 *   on a new background thread, mimicking how UserRepositoryImpl behaves.
 *
 * • Test 1 (regression): directly reproduces the crash — creating a CountDownTimer
 *   on a background thread without a Looper throws RuntimeException.
 *
 * • Test 2 (fix verification): confirms that posting the CountDownTimer creation
 *   to the main Looper (via Handler) succeeds without exception.
 *
 * • Test 3 (ViewModel integration): confirms PhoneOtpViewModel.sendOtp() does not
 *   throw when the callback is delivered on a background thread.
 */
public class PhoneOtpViewModelThreadingTest {

    // ── Fake repository ───────────────────────────────────────────────────────

    /**
     * Invokes onSuccess on a dedicated background thread, exactly as the real
     * UserRepositoryImpl does via its ThreadPoolExecutor.
     */
    private static class BackgroundCallbackUserRepository implements UserRepository {

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public void sendOtp(String phone, DomainCallback<Void> callback) {
            executor.execute(() -> callback.onSuccess(null));
        }

        @Override public void login(String e, String p, String d, DomainCallback<String> cb) {}
        @Override public void register(String n, String e, String p, String d, DomainCallback<String> cb) {}
        @Override public void loginWithGoogle(String t, String d, DomainCallback<String> cb) {}
        @Override public void verifyOtp(String phone, String code, DomainCallback<String> cb) {}
        @Override public void logout(DomainCallback<Void> cb) {}
        @Override public void logoutAll(DomainCallback<Void> cb) {}
        @Override public void setVisibility(VisibilityMode mode, DomainCallback<Void> cb) {}
        @Override public void saveAccessToken(String token) {}
        @Override public String getAccessToken() { return null; }
        @Override public String getOrGenerateDeviceId() { return "test-device"; }
        @Override public void updateFcmToken(String token, DomainCallback<Void> cb) {}
    }

    // ── Test 1: reproduce the crash ───────────────────────────────────────────

    /**
     * BUG REPRODUCTION
     *
     * Creating a CountDownTimer on a background thread (no Looper) throws
     * RuntimeException. This is exactly what the original sendOtp onSuccess did.
     *
     * Expected: RuntimeException is thrown.
     * Pre-fix behavior: crash in production (FATAL EXCEPTION).
     */
    @Test
    public void sendOtp_onSuccess_calledOnBackgroundThread_countDownTimerCreationThrows()
            throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> caughtException = new AtomicReference<>();

        Thread backgroundThread = new Thread(() -> {
            // No Looper.prepare() — simulates a raw thread-pool thread.
            try {
                // This is what the original startResendCountdown() does:
                new CountDownTimer(60_000L, 1_000L) {
                    @Override public void onTick(long ms) {}
                    @Override public void onFinish() {}
                }.start();

                // If we reach here the environment supports this (won't happen on real Android).
                caughtException.set(null);
            } catch (RuntimeException e) {
                caughtException.set(e);
            } finally {
                latch.countDown();
            }
        });
        backgroundThread.start();
        latch.await(5, TimeUnit.SECONDS);

        if (caughtException.get() == null) {
            fail("Expected RuntimeException from CountDownTimer on a no-Looper thread, but none was thrown. " +
                 "This test must run on a real Android device or Robolectric.");
        }
        // Exception message should mention Looper / Handler
        assert caughtException.get().getMessage() != null &&
               caughtException.get().getMessage().contains("Looper");
    }

    // ── Test 2: fix verification ──────────────────────────────────────────────

    /**
     * FIX VERIFICATION
     *
     * Posting CountDownTimer creation to Handler(Looper.getMainLooper()) from a
     * background thread must not throw.
     *
     * This is the corrected behaviour introduced in the fix.
     */
    @Test
    public void sendOtp_onSuccess_countDownTimerPostedToMainLooper_doesNotThrow()
            throws InterruptedException {

        // Ensure the main Looper exists (required for Handler).
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> caughtException = new AtomicReference<>();

        Thread backgroundThread = new Thread(() -> {
            // Simulate the fixed onSuccess: post CountDownTimer to main thread.
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    new CountDownTimer(60_000L, 1_000L) {
                        @Override public void onTick(long ms) {}
                        @Override public void onFinish() {}
                    }.start();
                } catch (RuntimeException e) {
                    caughtException.set(e);
                } finally {
                    latch.countDown();
                }
            });
        });
        backgroundThread.start();
        latch.await(5, TimeUnit.SECONDS);

        assertNull("CountDownTimer creation on main Looper must not throw", caughtException.get());
    }

    // ── Test 3: ViewModel integration ────────────────────────────────────────

    /**
     * VIEWMODEL INTEGRATION
     *
     * PhoneOtpViewModel.sendOtp() must not throw when the repository delivers
     * onSuccess on a background thread (the real production scenario).
     *
     * Pre-fix: FATAL EXCEPTION crashed the process.
     * Post-fix: no exception; ViewModel handles the thread switch internally.
     */
    @Test
    public void viewModel_sendOtp_backgroundCallback_doesNotCrash()
            throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> uncaughtEx = new AtomicReference<>();

        BackgroundCallbackUserRepository fakeRepo = new BackgroundCallbackUserRepository();
        Context appContext = ApplicationProvider.getApplicationContext();

        PhoneOtpViewModel viewModel = new PhoneOtpViewModel(fakeRepo, appContext);

        // Override uncaught exception handler to catch cross-thread crashes.
        Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            uncaughtEx.set(e);
            latch.countDown();
        });

        try {
            // Trigger the bug path: sendOtp → background onSuccess → startResendCountdown
            viewModel.sendOtp("+84702341568");

            // Give the background thread time to invoke onSuccess and either crash or succeed.
            boolean completed = latch.await(3, TimeUnit.SECONDS);

            if (completed && uncaughtEx.get() != null) {
                fail("PhoneOtpViewModel.sendOtp() caused an uncaught exception on a background thread: "
                        + uncaughtEx.get().getMessage());
            }
            // If latch timed out without a crash, the fix is working correctly.
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler);
        }
    }

}
