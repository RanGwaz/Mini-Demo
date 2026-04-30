package com.rangwaz.imagesocial.search;

import com.rangwaz.imagesocial.post.dto.PostView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopSearchIndexGateway implements SearchIndexGateway {

    private static final Logger log = LoggerFactory.getLogger(NoopSearchIndexGateway.class);

    @Override
    public void syncPost(PostView postView) {
        log.info("reserved search sync for post {}", postView.id());
    }
}
