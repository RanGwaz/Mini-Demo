package com.rangwaz.imagesocial.taxonomy;

import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.taxonomy.dto.PublishSuggestionsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taxonomy")
public class TaxonomyController {
    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/publish-suggestions")
    public ApiResponse<PublishSuggestionsResponse> publishSuggestions(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(taxonomyService.publishSuggestions(channel, keyword));
    }
}
