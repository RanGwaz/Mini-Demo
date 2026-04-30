package com.rangwaz.imagesocial.feed.dto;

import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.post.dto.PostView;

public record FeedHomeSnapshotResponse(
        PageResponse<PostView> page,
        FeedHomeDiagnosticsResponse diagnostics
) {
}
