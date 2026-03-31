package com.walkmate.data.repository;

import android.content.Context;

import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOCK implementation — bypasses Retrofit and accepts any credentials with a
 * simulated 1-second network delay.
 *
 * SessionManager is kept for local token persistence (EncryptedSharedPreferences)
 * so that code that calls getAccessToken() still receives a value across restarts.
 *
 * Replace login() and register() with real Retrofit calls when the backend is ready.
 */
public class UserRepositoryImpl implements UserRepository {

    private static final String MOCK_TOKEN = "mock-access-token-demo";

    private final SessionManager sessionManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public UserRepositoryImpl(Context context) {
        this.sessionManager = new SessionManager(context);
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void login(String email, String password, DomainCallback<String> callback) {
        executor.execute(() -> {
            sleep();
            saveAccessToken(MOCK_TOKEN);
            callback.onSuccess(MOCK_TOKEN);
        });
    }

    @Override
    public void register(String fullname, String email, String password, DomainCallback<Void> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(null);
        });
    }

    @Override
    public void saveAccessToken(String token) {
        sessionManager.saveAccessToken(token);
    }

    @Override
    public String getAccessToken() {
        return sessionManager.getAccessToken();
    }

    /**
     * Exposes SessionManager for repositories that may need it in the real
     * implementation (e.g., to build an authenticated Retrofit instance).
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
