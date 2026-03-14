package com.walkmate.application;

import com.walkmate.domain.intent.IntentRepository;
import com.walkmate.domain.intent.WalkIntent;
import com.walkmate.domain.valueobject.LocationSnapshot;
import com.walkmate.domain.valueobject.MatchingConstraints;
import com.walkmate.domain.valueobject.TimeWindow;
import com.walkmate.presentation.dto.request.SubmitIntentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmitWalkIntentService {
  private final IntentRepository intentRepository;

  @Transactional
  public WalkIntent execute(UUID userId, SubmitIntentRequest request) {
    WalkIntent intent = WalkIntent.create(
        UUID.randomUUID(),
        userId,
        new LocationSnapshot(request.lat(), request.lng()),
        new TimeWindow(request.startTime(), request.endTime()),
        request.purpose(),
        new MatchingConstraints(
            request.minAge(),
            request.maxAge(),
            request.genderPreference(),
            request.tagsPreference()
        ),
        Instant.now()
    );

    // Save intent. GiST EXCLUDE constraint acts as a safeguard against I-1 overlap.
    return intentRepository.save(intent);
  }
}
