package com.walkmate.application.hotspot;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotErrorCode;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HotspotQueryService {

    private final HotspotRepository hotspotRepository;

    @Transactional(readOnly = true)
    public List<Hotspot> getAllHotspots() {
        List<Hotspot> hotspots = hotspotRepository.findAll();
        if (hotspots.isEmpty()) {
            throw new DomainException(HotspotErrorCode.NO_HOTSPOT_AVAILABLE);
        }
        return hotspots;
    }

    @Transactional(readOnly = true)
    public Hotspot getHotspotById(String id) {
        return hotspotRepository.findById(id)
                .orElseThrow(() -> new DomainException(HotspotErrorCode.HOTSPOT_NOT_FOUND));
    }
}
