package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkproposal.WalkProposal;

import java.util.List;

public interface WalkIntentRepository {

    void createIntent(String hotspotId, String date, float timeStart, float timeEnd,
                      int ageMin, int ageMax, List<String> tags,
                      boolean isPrivate, String invitedFriendId,
                      DomainCallback<WalkIntent> callback);

    void listActiveIntents(DomainCallback<List<WalkIntent>> callback);

    void findMatch(String intentId, DomainCallback<WalkProposal> callback);

    void cancelIntent(String intentId, DomainCallback<Void> callback);
}
