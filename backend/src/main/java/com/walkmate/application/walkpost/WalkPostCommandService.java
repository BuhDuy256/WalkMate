package com.walkmate.application.walkpost;

import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.domain.walkpost.WalkPostErrorCode;
import com.walkmate.domain.walkpost.WalkPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalkPostCommandService {

    private final WalkSessionRepository walkSessionRepository;
    private final WalkPostRepository walkPostRepository;

    @Transactional
    public WalkPost createPost(CreateWalkPostCommand command) {
        WalkSession session = walkSessionRepository.findById(command.sessionId())
                .orElseThrow(() -> new DomainException(WalkPostErrorCode.WALK_POST_SESSION_NOT_FOUND));

        String authorId = command.authorId();

        boolean isParticipant = authorId.equals(session.getUserIdA())
                || authorId.equals(session.getUserIdB());
        if (!isParticipant) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_AUTHOR_NOT_PARTICIPANT);
        }

        SessionStatus callerStatus = authorId.equals(session.getUserIdA())
                ? session.getUserAStatus()
                : session.getUserBStatus();

        if (callerStatus != SessionStatus.COMPLETED) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_PERSONAL_STATUS_NOT_COMPLETED);
        }

        boolean callerMetricsFinalized = authorId.equals(session.getUserIdA())
                ? session.getUserAEndedAt() != null
                : session.getUserBEndedAt() != null;

        if (!callerMetricsFinalized) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_METRICS_NOT_FINALIZED);
        }

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_SESSION_NOT_POSTABLE);
        }

        if (walkPostRepository.existsBySessionAndAuthor(command.sessionId(), authorId)) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_DUPLICATED);
        }

        double distanceKm = authorId.equals(session.getUserIdA())
                ? session.getUserADistanceKm()
                : session.getUserBDistanceKm();
        long durationSeconds = authorId.equals(session.getUserIdA())
                ? session.getUserADurationSeconds()
                : session.getUserBDurationSeconds();

        PostVisibility visibility = PostVisibility.from(command.visibility());

        WalkPost post = WalkPost.create(
                command.sessionId(), authorId,
                command.caption(), visibility,
                command.showCompanion(), command.showRouteMap(), command.showStats(),
                distanceKm, durationSeconds
        );

        return walkPostRepository.save(post);
    }

    @Transactional
    public WalkPost updateVisibility(UpdateWalkPostVisibilityCommand command) {
        WalkPost post = walkPostRepository.findById(command.postId())
                .orElseThrow(() -> new DomainException(WalkPostErrorCode.WALK_POST_NOT_FOUND));

        post.changeVisibility(PostVisibility.from(command.newVisibility()), command.requesterId());

        return walkPostRepository.save(post);
    }

    @Transactional
    public void deletePost(String postId, String requesterId) {
        WalkPost post = walkPostRepository.findById(postId)
                .orElseThrow(() -> new DomainException(WalkPostErrorCode.WALK_POST_NOT_FOUND));

        if (!post.getAuthorId().equals(requesterId)) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_FORBIDDEN);
        }

        walkPostRepository.deleteById(postId);
    }
}
