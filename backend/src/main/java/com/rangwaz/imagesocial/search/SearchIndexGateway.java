package com.rangwaz.imagesocial.search;

import com.rangwaz.imagesocial.post.dto.PostView;

public interface SearchIndexGateway {

    void syncPost(PostView postView);
}
