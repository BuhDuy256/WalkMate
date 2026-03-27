package com.walkmate.data.repository;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HotspotRepositoryImpl implements HotspotRepository {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Mock data ─────────────────────────────────────────────────────────
    // TODO: Replace with Retrofit call — GET /api/v1/hotspots
    //       Expected: ApiResponse<List<HotspotResponse>> → map via HotspotMapper.toDomainList()
    private static final List<Hotspot> MOCK_HOTSPOTS = Arrays.asList(
            new Hotspot("1", "Công viên Tao Đàn",  10.77702, 106.69328, 12),
            new Hotspot("2", "Hồ Con Rùa",          10.78756, 106.69506,  8),
            new Hotspot("3", "Công viên Gia Định",  10.81289, 106.67790,  5),
            new Hotspot("4", "Công viên Lê Văn Tám",10.78720, 106.69863,  3)
    );

    @Override
    public void getHotspots(DomainCallback<List<Hotspot>> callback) {
        executor.execute(() -> callback.onSuccess(MOCK_HOTSPOTS));
    }

    @Override
    public void getHotspotById(String id, DomainCallback<Hotspot> callback) {
        executor.execute(() -> {
            for (Hotspot hotspot : MOCK_HOTSPOTS) {
                if (hotspot.getId().equals(id)) {
                    callback.onSuccess(hotspot);
                    return;
                }
            }
            callback.onError(new Exception("Hotspot not found: " + id));
        });
    }
}
