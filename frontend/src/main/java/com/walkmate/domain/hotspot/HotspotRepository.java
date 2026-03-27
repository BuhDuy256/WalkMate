package com.walkmate.domain.hotspot;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface HotspotRepository {
    void getHotspots(DomainCallback<List<Hotspot>> callback);
    void getHotspotById(String id, DomainCallback<Hotspot> callback);
}
