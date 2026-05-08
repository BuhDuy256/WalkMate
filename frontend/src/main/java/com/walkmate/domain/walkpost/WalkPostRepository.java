package com.walkmate.domain.walkpost;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface WalkPostRepository {
    void createPost(String sessionId, String caption, PostVisibility visibility,
                    boolean showCompanion, boolean showRouteMap, boolean showStats,
                    DomainCallback<WalkPost> callback);

    void getMyPosts(DomainCallback<List<WalkPost>> callback);

    void getUserPosts(String userId, DomainCallback<List<WalkPost>> callback);

    void updateVisibility(String postId, String visibility, DomainCallback<WalkPost> callback);

    void deletePost(String postId, DomainCallback<Void> callback);
}
