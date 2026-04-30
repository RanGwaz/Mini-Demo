package com.rangwaz.imagesocial.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUserInterestsRequest(
        @NotNull(message = "facets 不能为空")
        @Size(max = 80, message = "单次最多提交 80 个兴趣")
        List<@Valid UserInterestFacetPayload> facets
) {
}
