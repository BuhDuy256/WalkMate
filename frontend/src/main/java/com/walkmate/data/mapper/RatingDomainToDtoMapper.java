package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.SubmitRatingRequestDto;
import com.walkmate.domain.rating.Rating;
import com.walkmate.domain.rating.RatingTag;

import java.util.List;
import java.util.stream.Collectors;


public class RatingDomainToDtoMapper {

    public SubmitRatingRequestDto mapToDto(Rating rating) {
        List<String> tagCodes = rating.getTags() != null
                ? rating.getTags().stream()
                .map(RatingTag::toCode)
                .collect(Collectors.toList())
                : List.of();

        return new SubmitRatingRequestDto(
                rating.getReviewerId(),
                rating.getSessionId(),
                rating.getRevieweeId(),
                rating.getScore().getValue(),
                tagCodes,
                rating.getComment().getValue()
        );
    }
}
