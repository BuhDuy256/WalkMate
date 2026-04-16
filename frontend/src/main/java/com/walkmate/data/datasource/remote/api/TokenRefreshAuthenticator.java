package com.walkmate.data.datasource.remote.api;

import com.walkmate.core.event.AuthEventBus;
import com.walkmate.data.datasource.remote.dto.request.user.RefreshTokenRequestDto;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.user.LoginResponseDto;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * OkHttp Authenticator that silently rotates the access token when a 401 is received.
 *
 * Thread safety: A static ReentrantLock ensures only one thread performs the refresh
 * at a time. Concurrent 401 threads check whether the token was already rotated before
 * attempting their own refresh (stale-token check).
 *
 * Infinite-loop guard: If the /auth/refresh endpoint itself returns 401 (revoked or
 * expired refresh token), this method returns null immediately, clears the session,
 * and fires a FORCE_LOGOUT event — preventing recursive invocation.
 */
public class TokenRefreshAuthenticator implements Authenticator {

    private static final ReentrantLock REFRESH_LOCK = new ReentrantLock();

    private final SessionManager sessionManager;
    private final AuthApiService authApiService;

    public TokenRefreshAuthenticator(SessionManager sessionManager, AuthApiService authApiService) {
        this.sessionManager = sessionManager;
        this.authApiService = authApiService;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        // Infinite-loop guard: the refresh endpoint itself returned 401.
        // Clear session and signal the UI — do not retry.
        if (response.request().url().toString().contains("/auth/refresh")) {
            sessionManager.clearSession();
            AuthEventBus.getInstance().postForceLogout();
            return null;
        }

        // Snapshot the token as seen by this thread before acquiring the lock.
        String tokenAtEntry = sessionManager.getAccessToken();

        REFRESH_LOCK.lock();
        try {
            // Stale-token check: if the token changed while this thread was waiting,
            // another thread already performed the refresh. Retry with the new token.
            String currentToken = sessionManager.getAccessToken();
            if (tokenAtEntry != null && !tokenAtEntry.equals(currentToken) && currentToken != null) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentToken)
                        .build();
            }

            // This thread is responsible for the refresh.
            String refreshToken = sessionManager.getRefreshToken();
            if (refreshToken == null) {
                sessionManager.clearSession();
                AuthEventBus.getInstance().postForceLogout();
                return null;
            }

            retrofit2.Response<ApiResponse<LoginResponseDto>> refreshResp = authApiService
                    .refreshToken(new RefreshTokenRequestDto(refreshToken))
                    .execute();

            if (refreshResp.isSuccessful()
                    && refreshResp.body() != null
                    && refreshResp.body().isSuccess()
                    && refreshResp.body().getData() != null) {

                LoginResponseDto data = refreshResp.body().getData();
                sessionManager.saveAccessToken(data.getAccessToken());
                sessionManager.saveRefreshToken(data.getRefreshToken());

                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + data.getAccessToken())
                        .build();
            } else {
                sessionManager.clearSession();
                AuthEventBus.getInstance().postForceLogout();
                return null;
            }
        } finally {
            REFRESH_LOCK.unlock(); // Always released — even on IOException
        }
    }
}
