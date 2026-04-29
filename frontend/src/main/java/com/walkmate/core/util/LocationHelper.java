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

    /**
     * Resolves a human-readable place name (park, sub-district, or city) for
     * a raw lat/lng pair. Used for the Recent Mates walk location on the Home screen.
     *
     * Priority: feature name (park/building) → sub-locality → locality → null.
     * Delivers null on failure so callers can hide the location row gracefully.
     */
    public static void resolveLocationName(Context context, double lat, double lng,
                                           LocationNameCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String name = null;
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    String feature = addr.getFeatureName();
                    if (feature != null && !feature.isEmpty() && !feature.matches("\\d+.*")) {
                        name = feature;
                    } else if (addr.getSubLocality() != null) {
                        name = addr.getSubLocality();
                    } else if (addr.getLocality() != null) {
                        name = addr.getLocality();
                    }
                }
            } catch (IOException ignored) {}
            final String result = name;
            mainHandler.post(() -> callback.onResolved(result));
        });
    }
}
