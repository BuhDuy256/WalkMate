package com.walkmate.domain.hotspot;

import java.util.List;
import java.util.Optional;

public interface HotspotRepository {
    List<Hotspot> findAll();
    Optional<Hotspot> findById(String id);
}
