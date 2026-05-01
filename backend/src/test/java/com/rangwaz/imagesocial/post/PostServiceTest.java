package com.rangwaz.imagesocial.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.PostAsset;
import com.rangwaz.imagesocial.domain.entity.User;
import com.rangwaz.imagesocial.domain.mapper.ContentReportMapper;
import com.rangwaz.imagesocial.domain.mapper.PostAssetMapper;
import com.rangwaz.imagesocial.domain.mapper.PostCommentMapper;
import com.rangwaz.imagesocial.domain.mapper.PostFavoriteMapper;
import com.rangwaz.imagesocial.domain.mapper.PostLikeMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.PostNegativeFeedbackMapper;
import com.rangwaz.imagesocial.event.EventService;
import com.rangwaz.imagesocial.post.dto.CreatePostRequest;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.search.SearchIndexGateway;
import com.rangwaz.imagesocial.taxonomy.ContentChannel;
import com.rangwaz.imagesocial.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostAssetMapper postAssetMapper;
    @Mock
    private PostLikeMapper postLikeMapper;
    @Mock
    private PostFavoriteMapper postFavoriteMapper;
    @Mock
    private PostCommentMapper postCommentMapper;
    @Mock
    private PostNegativeFeedbackMapper postNegativeFeedbackMapper;
    @Mock
    private ContentReportMapper contentReportMapper;
    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;
    @Mock
    private SearchIndexGateway searchIndexGateway;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postMapper,
                postAssetMapper,
                postLikeMapper,
                postFavoriteMapper,
                postCommentMapper,
                postNegativeFeedbackMapper,
                contentReportMapper,
                userService,
                eventService,
                searchIndexGateway
        );
    }

    @Test
    void createsTextPostWithoutAssetsAndStoresChannelSeparatelyFromTags() {
        Long authorId = 7L;
        User author = new User();
        author.setId(authorId);
        author.setUsername("creator");
        author.setNickname("Creator");
        UserSummary summary = new UserSummary(authorId, "creator", null, "Creator", null, null, null);

        when(userService.requireById(authorId)).thenReturn(author);
        when(userService.toSummary(author)).thenReturn(summary);
        when(postAssetMapper.selectByPostId(100L)).thenReturn(List.of());
        when(postMapper.insert(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(100L);
            return 1;
        });

        CreatePostRequest request = new CreatePostRequest(
                "纯文字标题",
                "没有图片也应该能发布",
                ContentChannel.CAMPUS_LIFE.key(),
                List.of("大学生校园生活", "学习笔记", "效率工具"),
                List.of()
        );

        PostView view = postService.createPost(authorId, request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(postCaptor.capture());
        Post insertedPost = postCaptor.getValue();

        assertThat(insertedPost.getCoverUrl()).isEmpty();
        assertThat(insertedPost.getTags()).isEqualTo("学习笔记,效率工具");
        assertThat(insertedPost.getTopicPath()).isEqualTo(ContentChannel.CAMPUS_LIFE.topicPath());
        assertThat(insertedPost.getTopicClusterKey()).isEqualTo(ContentChannel.CAMPUS_LIFE.key());
        assertThat(insertedPost.getTaxonomyVersion()).isEqualTo(ContentChannel.TAXONOMY_VERSION);
        assertThat(view.assets()).isEmpty();
        assertThat(view.channel()).isEqualTo(ContentChannel.CAMPUS_LIFE.key());
        assertThat(view.tags()).containsExactly("学习笔记", "效率工具");
        verify(postAssetMapper, never()).insert(any(PostAsset.class));
    }

    @Test
    void rejectsUnknownChannelKeys() {
        when(userService.requireById(7L)).thenReturn(new User());

        CreatePostRequest request = new CreatePostRequest(
                "标题",
                "内容",
                "unknown_channel",
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> postService.createPost(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频道不存在");
    }
}
