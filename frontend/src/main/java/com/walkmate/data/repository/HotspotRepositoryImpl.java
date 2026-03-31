package com.walkmate.data.repository;

import android.content.Context;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOCK implementation — bypasses Retrofit and returns hardcoded Ho Chi Minh City
 * hotspot data with a simulated 1-second network delay.
 * Replace each method body with real Retrofit calls when the backend is ready.
 */
public class HotspotRepositoryImpl implements HotspotRepository {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public HotspotRepositoryImpl(Context context) {
        // Context retained for future real implementation
    }

    // ---------------------------------------------------------------------------
    // Mock data — Ho Chi Minh City hotspots with real GPS coordinates
    // ---------------------------------------------------------------------------

    private static final List<Hotspot> MOCK_HOTSPOTS = Collections.unmodifiableList(Arrays.asList(
            new Hotspot("hs-tao-dan",     "Công viên Tao Đàn",          10.77413, 106.68863, 12),
            new Hotspot("hs-nguyen-hue",  "Phố đi bộ Nguyễn Huệ",       10.77256, 106.70262, 28),
            new Hotspot("hs-ho-con-rua",  "Hồ Con Rùa",                  10.77352, 106.69327, 15),
            new Hotspot("hs-gia-dinh",    "Công viên Gia Định",           10.81348, 106.68372,  9),
            new Hotspot("hs-le-van-tam",  "Công viên Lê Văn Tám",         10.78670, 106.69680,  6)
    ));

    private static final Map<String, Hotspot> MOCK_HOTSPOT_MAP;
    static {
        Map<String, Hotspot> map = new HashMap<>();
        for (Hotspot h : MOCK_HOTSPOTS) {
            map.put(h.getId(), h);
        }
        MOCK_HOTSPOT_MAP = Collections.unmodifiableMap(map);
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void getHotspots(DomainCallback<List<Hotspot>> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(MOCK_HOTSPOTS);
        });
    }

    @Override
    public void getHotspotById(String id, DomainCallback<Hotspot> callback) {
        executor.execute(() -> {
            sleep();
            Hotspot hotspot = MOCK_HOTSPOT_MAP.get(id);
            if (hotspot != null) {
                callback.onSuccess(hotspot);
            } else {
                callback.onError(new Exception("Hotspot not found: " + id));
            }
        });
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
