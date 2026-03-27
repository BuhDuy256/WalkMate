package com.walkmate.data.datasource.remote.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static final String BASE_URL = "http://10.0.2.2:8080/";

    private static final HttpLoggingInterceptor LOGGING_INTERCEPTOR =
            new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY);

    // ── Public client — no auth header (login, register) ──────────────────
    private static final OkHttpClient PUBLIC_CLIENT = new OkHttpClient.Builder()
            .addInterceptor(LOGGING_INTERCEPTOR)
            .build();

    private static final Retrofit PUBLIC_RETROFIT = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(PUBLIC_CLIENT)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    private ApiClient() {
    }

    public static AuthApiService getAuthApiService() {
        return PUBLIC_RETROFIT.create(AuthApiService.class);
    }

    // ── Authenticated client — attaches Bearer token from SessionManager ──
    public static Retrofit buildAuthenticatedRetrofit(SessionManager sessionManager) {
        OkHttpClient authenticatedClient = new OkHttpClient.Builder()
                .addInterceptor(LOGGING_INTERCEPTOR)
                .addInterceptor(new AuthInterceptor(sessionManager))
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(authenticatedClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
