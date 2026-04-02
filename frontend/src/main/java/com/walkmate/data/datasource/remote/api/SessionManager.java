package com.walkmate.data.datasource.remote.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Secure token storage backed by EncryptedSharedPreferences.
 *
 * Requires dependency in build.gradle:
 *   implementation "androidx.security:security-crypto:1.1.0-alpha06"
 *
 * Usage: instantiate once with Application context (e.g. in UserRepositoryImpl constructor).
 */
public class SessionManager {

    private static final String PREFS_NAME = "walkmate_secure_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = buildEncryptedPrefs(context.getApplicationContext());
    }

    public void saveAccessToken(String token) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    /**
     * Returns true only when a non-blank, non-expired JWT is available.
     */
    public boolean hasUsableAccessToken() {
        String token = getAccessToken();
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        return !isTokenExpired(token);
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    /**
     * Extracts the user ID from the JWT {@code sub} claim by Base64-decoding the
     * payload section. Returns null if no token is stored or decoding fails.
     */
    public String getUserId() {
        String token = getAccessToken();
        if (token == null) return null;
        try {
            JSONObject json = decodePayload(token);
            return json.optString("sub", null);
        } catch (Exception e) {
            Log.w("SessionManager", "Failed to decode JWT sub claim", e);
            return null;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            JSONObject json = decodePayload(token);
            if (!json.has("exp")) {
                return true;
            }
            long expSeconds = json.getLong("exp");
            long nowSeconds = System.currentTimeMillis() / 1000L;
            return expSeconds <= nowSeconds;
        } catch (Exception e) {
            Log.w("SessionManager", "Failed to decode JWT exp claim", e);
            return true;
        }
    }

    private JSONObject decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));
    }

    private static SharedPreferences buildEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialise EncryptedSharedPreferences", e);
        }
    }
}
