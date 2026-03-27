package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.DomainCallback;

public interface WalkIntentRepository {
    void createIntent(String hotspotId, float timeStart, float timeEnd,
                      int ageMin, int ageMax, DomainCallback<WalkIntent> callback);

    void findMatch(String intentId, DomainCallback<WalkIntent> callback);

    void cancelIntent(String intentId, DomainCallback<Void> callback);
}
