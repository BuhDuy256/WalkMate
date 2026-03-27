package com.walkmate.data.datasource.remote.api;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
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

    public void clearSession() {
        prefs.edit().clear().apply();
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
