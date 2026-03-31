package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface WalkIntentRepository {
    void createIntent(String hotspotId, float timeStart, float timeEnd,
                      int ageMin, int ageMax, DomainCallback<WalkIntent> callback);

    void listActiveIntents(DomainCallback<List<WalkIntent>> callback);

    void findMatch(String intentId, DomainCallback<WalkIntent> callback);

    void cancelIntent(String intentId, DomainCallback<Void> callback);
}
