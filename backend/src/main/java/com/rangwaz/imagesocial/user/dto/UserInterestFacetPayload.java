package com.rangwaz.imagesocial.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UserInterestFacetPayload(
        @Size(max = 32, message = "facetType 长度不能超过 32")
        String facetType,
        @NotBlank(message = "facetKey 不能为空")
        @Size(max = 128, message = "facetKey 长度不能超过 128")
        String facetKey,
        @Size(max = 255, message = "facetLabel 长度不能超过 255")
        String facetLabel,
        @DecimalMin(value = "0.1", message = "weight 不能小于 0.1")
        @DecimalMax(value = "5.0", message = "weight 不能大于 5.0")
        BigDecimal weight
) {
}
