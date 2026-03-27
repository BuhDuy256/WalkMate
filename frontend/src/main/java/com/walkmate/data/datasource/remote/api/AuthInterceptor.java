package com.walkmate.data.datasource.remote.api;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that attaches the stored JWT as a Bearer token to every
 * outgoing request. Add this interceptor only to the authenticated OkHttpClient
 * (not to the public client used for login/register).
 */
public class AuthInterceptor implements Interceptor {

    private final SessionManager sessionManager;

    public AuthInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = sessionManager.getAccessToken();

        Request original = chain.request();

        if (token == null || token.isEmpty()) {
            // No token available — proceed without auth header
            // The backend will return 401 if the endpoint requires authentication
            return chain.proceed(original);
        }

        Request authenticated = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();

        return chain.proceed(authenticated);
    }
}
