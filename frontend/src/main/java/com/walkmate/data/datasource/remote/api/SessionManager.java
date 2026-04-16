package com.walkmate.data.datasource.remote.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.util.UUID;

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
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_DEVICE_ID = "device_id";

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

    public void saveRefreshToken(String token) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply();
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    /**
     * Returns the persisted device ID, generating and saving a new UUID if none exists.
     * This ID is intentionally NOT cleared on logout — it survives session changes.
     */
    public String getOrGenerateDeviceId() {
        String existingId = prefs.getString(KEY_DEVICE_ID, null);
        if (existingId != null) return existingId;
        String newId = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_DEVICE_ID, newId).commit();
        return newId;
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
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .apply();
        // KEY_DEVICE_ID is intentionally preserved — survives logout
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
            return createEncryptedPrefs(context);
        } catch (GeneralSecurityException | IOException e) {
            // The Keystore key is gone or the stored keyset is corrupted — this happens
            // after a reinstall on some devices, or when a backup is restored to a new
            // device whose Keystore doesn't hold the original key.
            // Deleting the stale prefs file lets us create a fresh keyset on the retry.
            // The stored tokens are lost; the user will need to log in again.
            Log.w("SessionManager", "EncryptedSharedPreferences keyset corrupted or key missing; "
                    + "wiping stale prefs and retrying.", e);
            context.deleteSharedPreferences(PREFS_NAME);
            try {
                return createEncryptedPrefs(context);
            } catch (GeneralSecurityException | IOException retryEx) {
                throw new RuntimeException("Failed to initialise EncryptedSharedPreferences", retryEx);
            }
        }
    }

    private static SharedPreferences createEncryptedPrefs(Context context)
            throws GeneralSecurityException, IOException {
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
    }
}
