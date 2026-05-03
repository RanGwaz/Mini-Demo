package com.rangwaz.imagesocial.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxonomyServiceTest {

    @Test
    void publishSuggestionsMergeSeedAndRecentPostTags() {
        PostMapper postMapper = mock(PostMapper.class);
        Post post = new Post();
        post.setTags("胶片,街拍,摄影");
        post.setSemanticTags("城市探索");
        post.setStyleTags("日常记录");
        when(postMapper.selectRecentTagSamples(30, 500)).thenReturn(List.of(post));

        TaxonomyService service = new TaxonomyService(postMapper);
        var response = service.publishSuggestions("photography", "街");

        assertThat(response.quickTags()).contains("街拍");
        assertThat(response.trendingTags())
                .anyMatch(tag -> "街拍".equals(tag.name()) && tag.postCount() > 0);
    }
}
