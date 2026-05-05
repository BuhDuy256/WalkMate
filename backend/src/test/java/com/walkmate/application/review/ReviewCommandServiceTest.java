package com.walkmate.application.review;

import com.walkmate.application.gamification.BadgeEvaluationService;
import com.walkmate.application.walkintent.AiTrainingService;
import com.walkmate.domain.review.ReviewTagRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.review.WalkReviewRepository;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

class ReviewCommandServiceTest {

    @Mock
    private WalkSessionRepository walkSessionRepository;

    @Mock
    private WalkReviewRepository walkReviewRepository;

    @Mock
    private ReviewTagRepository reviewTagRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BadgeEvaluationService badgeEvaluationService;

    @Mock
    private AiTrainingService aiTrainingService;

    private ReviewCommandService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ReviewCommandService(walkSessionRepository, walkReviewRepository,
                reviewTagRepository, userRepository, badgeEvaluationService, aiTrainingService);
    }

    @Test
    void submitReview_success_appliesTrustDelta_andSavesMappings_andTriggersAi() {
        String sessionId = "s-1";
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();
        String reviewer = userA;
        String reviewee = userB;

        WalkSession session = new WalkSession(
                sessionId, null, userA, userB,
                null, null, 0.0, 0.0,
                Instant.now(), Instant.now(), SessionStatus.COMPLETED,
                null, null, null, null, null,
                null, null, 0L, SessionStatus.COMPLETED, SessionStatus.COMPLETED,
                null, null, 0.0, 0L, 0.0, 0L, null, null
        );

        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(walkReviewRepository.existsBySessionAndReviewer(sessionId, reviewer)).thenReturn(false);
        when(walkReviewRepository.save(any(WalkReview.class))).thenAnswer(i -> i.getArgument(0));

        UUID revieweeUuid = UUID.fromString(reviewee);
        User user = new User(revieweeUuid, "a@b.com", null, null, null, null, null,
                null, null, 500, 0, 0.0, 0, null, null);

        when(userRepository.findById(reviewee)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        doNothing().when(reviewTagRepository).saveTagMappings(anyString(), anyList());
        when(reviewTagRepository.findByIds(anyList())).thenReturn(List.of());
        doNothing().when(badgeEvaluationService).evaluateAndAward(any(User.class));
        doNothing().when(aiTrainingService).trainWeightsFromReview(any(UUID.class), anyList());

        List<UUID> tags = List.of(UUID.randomUUID());

        WalkReview out = service.submitReview(sessionId, reviewer, 5, "Nice", tags);

        assertThat(out.getRatingStars()).isEqualTo(5);

        // Trust delta for 5 stars is +10 → new score 510
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getTrustScore()).isEqualTo(510);

        // mappings saved
        verify(reviewTagRepository).saveTagMappings(out.getReviewId(), tags);

        // AI training triggered
        verify(aiTrainingService).trainWeightsFromReview(UUID.fromString(reviewer), reviewTagRepository.findByIds(tags));
    }

    @Test
    void submitReview_sessionNotCompleted_throws() {
        String sessionId = "s-2";
        WalkSession session = new WalkSession(
                sessionId, null, "a","b",
                null, null, 0.0, 0.0,
                Instant.now(), Instant.now(), SessionStatus.PENDING,
                null, null, null, null, null,
                null, null, 0L, SessionStatus.PENDING, SessionStatus.PENDING,
                null, null, 0.0, 0L, 0.0, 0L, null, null
        );
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        DomainException ex = assertThrows(DomainException.class,
                () -> service.submitReview(sessionId, "a", 5, null, List.of()));
        assertThat(ex.getErrorCode()).isEqualTo(com.walkmate.domain.review.ReviewErrorCode.REVIEW_SESSION_NOT_COMPLETED);
    }

    @Test
    void submitReview_notParticipant_throws() {
        String sessionId = "s-3";
        WalkSession session = new WalkSession(
                sessionId, null, "x","y",
                null, null, 0.0, 0.0,
                Instant.now(), Instant.now(), SessionStatus.COMPLETED,
                null, null, null, null, null,
                null, null, 0L, SessionStatus.COMPLETED, SessionStatus.COMPLETED,
                null, null, 0.0, 0L, 0.0, 0L, null, null
        );
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        DomainException ex = assertThrows(DomainException.class,
                () -> service.submitReview(sessionId, "not-in-session", 4, null, List.of()));
        assertThat(ex.getErrorCode()).isEqualTo(com.walkmate.domain.review.ReviewErrorCode.REVIEW_NOT_PARTICIPANT);
    }

    @Test
    void submitReview_alreadySubmitted_throws() {
        String sessionId = "s-4";
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(new WalkSession(
                sessionId, null, "a","b",
                null, null, 0.0, 0.0,
                Instant.now(), Instant.now(), SessionStatus.COMPLETED,
                null, null, null, null, null,
                null, null, 0L, SessionStatus.COMPLETED, SessionStatus.COMPLETED,
                null, null, 0.0, 0L, 0.0, 0L, null, null
        )));
        when(walkReviewRepository.existsBySessionAndReviewer(sessionId, "a")).thenReturn(true);

        DomainException ex = assertThrows(DomainException.class,
                () -> service.submitReview(sessionId, "a", 3, null, List.of()));
        assertThat(ex.getErrorCode()).isEqualTo(com.walkmate.domain.review.ReviewErrorCode.REVIEW_ALREADY_SUBMITTED);
    }

    @Test
    void submitReview_userNotFound_throws() {
        String sessionId = "s-5";
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();
        when(walkSessionRepository.findById(sessionId)).thenReturn(Optional.of(new WalkSession(
                sessionId, null, userA,userB,
                null, null, 0.0, 0.0,
                Instant.now(), Instant.now(), SessionStatus.COMPLETED,
                null, null, null, null, null,
                null, null, 0L, SessionStatus.COMPLETED, SessionStatus.COMPLETED,
                null, null, 0.0, 0L, 0.0, 0L, null, null
        )));
        when(walkReviewRepository.existsBySessionAndReviewer(sessionId, userA)).thenReturn(false);
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> service.submitReview(sessionId, userA, 2, null, List.of()));
        assertThat(ex.getErrorCode()).isEqualTo(com.walkmate.domain.user.UserErrorCode.USER_NOT_FOUND);
    }
}
