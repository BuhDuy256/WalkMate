package com.walkmate.application.walkpost;

import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.domain.walkpost.WalkPostErrorCode;
import com.walkmate.domain.walkpost.WalkPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalkPostQueryService {

    private final WalkPostRepository walkPostRepository;
    private final SocialRepository socialRepository;

    public List<WalkPost> getMyPosts(String authorId) {
        return walkPostRepository.findByAuthor(authorId);
    }

    public List<WalkPost> getUserPosts(String authorId, String viewerId) {
        if (viewerId == null) {
            return walkPostRepository.findVisibleByAuthor(authorId, false);
        }

        if (viewerId.equals(authorId)) {
            return walkPostRepository.findByAuthor(authorId);
        }

        UUID authorUuid = UUID.fromString(authorId);
        UUID viewerUuid = UUID.fromString(viewerId);

        boolean blocked = socialRepository.isBlocked(authorUuid, viewerUuid)
                || socialRepository.isBlocked(viewerUuid, authorUuid);
        if (blocked) {
            return Collections.emptyList();
        }

        boolean isFriend = socialRepository.areAcceptedFriends(viewerUuid, authorUuid);
        return walkPostRepository.findVisibleByAuthor(authorId, isFriend);
    }

    public WalkPost getPostById(String postId) {
        return walkPostRepository.findById(postId)
                .orElseThrow(() -> new DomainException(WalkPostErrorCode.WALK_POST_NOT_FOUND));
    }

    /** Returns the Supabase Storage path of the route preview image, or null if none. */
    public String getRoutePreviewPath(String postId) {
        return walkPostRepository.findById(postId)
                .map(WalkPost::getRoutePreviewPath)
                .orElse(null);
    }
}
