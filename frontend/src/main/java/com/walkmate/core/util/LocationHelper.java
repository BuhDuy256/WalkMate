package com.walkmate.core.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Static utility for resolving a GPS {@link Location} to a human-readable city name.
 *
 * Geocoding is always performed on a background thread (Geocoder.getFromLocation()
 * performs network I/O and must never be called on the main thread). The result is
 * delivered to the caller on the main thread via {@link LocationNameCallback}.
 */
public final class LocationHelper {

    private LocationHelper() {}

    public interface LocationNameCallback {
        void onResolved(String cityName);
    }

    /**
     * Resolves the nearest city name for the given {@link Location}.
     * Calls back on the main thread. Falls back to {@code "Your area"} on failure.
     *
     * @param context   Application context (used to construct {@link Geocoder})
     * @param location  The GPS fix to reverse-geocode
     * @param callback  Receives the city name on the main thread
     */
    public static void resolveCity(Context context, Location location,
                                   LocationNameCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String city = "Your area";
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(
                        location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String locality = addresses.get(0).getLocality();
                    if (locality != null) city = locality;
                }
            } catch (IOException ignored) {}
            final String result = city;
            mainHandler.post(() -> callback.onResolved(result));
        });
    }
}
