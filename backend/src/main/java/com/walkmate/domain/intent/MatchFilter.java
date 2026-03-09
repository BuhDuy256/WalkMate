package com.walkmate.domain.intent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Value Object to store match_filter JSONB data based on db.sql
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchFilter {
    private Integer minAge;
    private Integer maxAge;
    private String genderPreference;
    private List<String> tagsPreference;
    private Integer searchRadiusMeters;
}
