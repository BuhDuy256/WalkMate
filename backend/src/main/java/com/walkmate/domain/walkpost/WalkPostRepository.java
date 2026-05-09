package com.walkmate.domain.walkpost;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface WalkPostRepository {
    WalkPost save(WalkPost post);
    Optional<WalkPost> findById(String postId);
    boolean existsBySessionAndAuthor(String sessionId, String authorId);
    Optional<String> findPostIdBySessionAndAuthor(String sessionId, String authorId);
    List<WalkPost> findByAuthor(String authorId);
    List<WalkPost> findVisibleByAuthor(String authorId, boolean isFriend);
    Map<String, Boolean> findExistenceMapBySessionIdsAndAuthor(Set<String> sessionIds, String authorId);
    void deleteById(String postId);
    void updateRoutePreview(String postId, String routePreviewUrl, String routePreviewPath, String routePreviewStatus);
}
