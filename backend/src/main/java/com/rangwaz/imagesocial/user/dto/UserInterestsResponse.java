package com.rangwaz.imagesocial.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserInterestsResponse(
        Long userId,
        List<UserInterestFacetView> facets,
        LocalDateTime updatedAt
) {
}
