package com.walkmate.application.review;

import com.walkmate.domain.review.ReviewTag;
import com.walkmate.domain.review.ReviewTagRepository;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.review.WalkReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    private final ReviewTagRepository  reviewTagRepository;
    private final WalkReviewRepository walkReviewRepository;

    @Transactional(readOnly = true)
    public List<ReviewTag> getActiveTags() {
        return reviewTagRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public List<WalkReview> getReviewsForUser(String userId) {
        return walkReviewRepository.findByRevieweeId(userId);
    }
}
