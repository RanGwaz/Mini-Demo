package com.rangwaz.imagesocial.feed.service;

import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsResponse;
import com.rangwaz.imagesocial.feed.dto.FeedHomeSnapshotResponse;
import com.rangwaz.imagesocial.post.dto.PostView;

public interface FeedService {

    PageResponse<PostView> homeFeed(Long currentUserId,
                                    int page,
                                    int size,
                                    String seed,
                                    String topicFilter,
                                    String styleFilter,
                                    String tagFilter);

    FeedHomeDiagnosticsResponse homeFeedDiagnostics(Long currentUserId,
                                                    int page,
                                                    int size,
                                                    String seed,
                                                    String topicFilter,
                                                    String styleFilter,
                                                    String tagFilter);

    FeedHomeSnapshotResponse homeFeedSnapshot(Long currentUserId,
                                              int page,
                                              int size,
                                              String seed,
                                              String topicFilter,
                                              String styleFilter,
                                              String tagFilter);

    PageResponse<PostView> similarPosts(Long currentUserId,
                                        Long postId,
                                        int page,
                                        int size,
                                        String topicFilter,
                                        String styleFilter,
                                        String tagFilter);
}
