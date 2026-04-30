package com.rangwaz.imagesocial.user.dto;

import java.math.BigDecimal;

public record UserInterestFacetView(
        String facetType,
        String facetKey,
        String facetLabel,
        BigDecimal weight
) {
}
