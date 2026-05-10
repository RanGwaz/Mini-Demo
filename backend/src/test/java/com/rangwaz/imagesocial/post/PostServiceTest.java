package com.rangwaz.imagesocial.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.channel.ChannelService;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.PostAsset;
import com.rangwaz.imagesocial.domain.entity.Topic;
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
import com.rangwaz.imagesocial.topic.PostTopicService;
import com.rangwaz.imagesocial.topic.TopicService;
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
    @Mock
    private ChannelService channelService;
    @Mock
    private TopicService topicService;
    @Mock
    private PostTopicService postTopicService;

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
                searchIndexGateway,
                channelService,
                topicService,
                postTopicService,
                new ObjectMapper()
        );
    }

    @Test
    void createsTextPostAndWritesPostTopics() {
        Long authorId = 7L;
        User author = author(authorId);
        UserSummary summary = new UserSummary(authorId, "creator", null, "Creator", null, null, null, "ROLE_USER");
        Channel campus = channel("campus", "Campus", "campus_post");
        Topic study = topic(11L, "Study notes", "study-notes");
        Topic tools = topic(12L, "Productivity tools", "productivity-tools");

        when(userService.requireById(authorId)).thenReturn(author);
        when(userService.toSummary(author)).thenReturn(summary);
        when(channelService.findByCode("campus")).thenReturn(campus);
        when(topicService.resolvePublishTopics(any(), any(), any(), eq(campus), eq(authorId)))
                .thenReturn(List.of(study, tools));
        when(postAssetMapper.selectByPostId(100L)).thenReturn(List.of());
        when(postMapper.insert(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(100L);
            return 1;
        });

        CreatePostRequest request = new CreatePostRequest(
                "Text post",
                "Plain text content can be published without images",
                "campus",
                List.of("Study notes", "Productivity tools"),
                List.of()
        );

        PostView view = postService.createPost(authorId, request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(postCaptor.capture());
        Post insertedPost = postCaptor.getValue();

        assertThat(insertedPost.getCoverUrl()).isEmpty();
        assertThat(insertedPost.getTags()).isEqualTo("Study notes,Productivity tools");
        assertThat(insertedPost.getChannelCode()).isEqualTo("campus");
        assertThat(insertedPost.getPostType()).isEqualTo("campus_post");
        assertThat(insertedPost.getExtra()).isEqualTo("{}");
        assertThat(insertedPost.getTopicPath()).isEqualTo("Campus/Study notes/Productivity tools");
        assertThat(insertedPost.getSemanticTags()).contains("campus", "Study notes", "study-notes");
        assertThat(insertedPost.getStyleTags()).isEqualTo("text");
        assertThat(insertedPost.getTopicClusterKey()).isEqualTo("campus");
        assertThat(insertedPost.getSubtopicClusterKey()).isEqualTo("study-notes");
        assertThat(insertedPost.getTaxonomyVersion()).isEqualTo("db-channel-topic-v1");
        assertThat(insertedPost.getQualityScore()).isPositive();
        assertThat(insertedPost.getSafetyScore()).isEqualByComparingTo("1.0000");
        assertThat(view.assets()).isEmpty();
        assertThat(view.channel()).isEqualTo("campus");
        assertThat(view.channelCode()).isEqualTo("campus");
        assertThat(view.postType()).isEqualTo("campus_post");
        assertThat(view.extra()).isEmpty();
        assertThat(view.tags()).containsExactly("Study notes", "Productivity tools");
        verify(postTopicService).replaceUserTopics(100L, List.of(11L, 12L));
        verify(postAssetMapper, never()).insert(any(PostAsset.class));
    }

    @Test
    void fallsBackToDefaultTitleWhenTitleIsBlank() {
        Long authorId = 7L;
        User author = author(authorId);
        UserSummary summary = new UserSummary(authorId, "creator", null, "Creator", null, null, null, "ROLE_USER");
        Channel campus = channel("campus", "Campus", "campus_post");

        when(userService.requireById(authorId)).thenReturn(author);
        when(userService.toSummary(author)).thenReturn(summary);
        when(channelService.findByCode("campus")).thenReturn(campus);
        when(topicService.resolvePublishTopics(any(), any(), any(), eq(campus), eq(authorId))).thenReturn(List.of());
        when(postAssetMapper.selectByPostId(101L)).thenReturn(List.of());
        when(postMapper.insert(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(101L);
            return 1;
        });

        CreatePostRequest request = new CreatePostRequest("   ", "", "campus", List.of(), List.of());

        postService.createPost(authorId, request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(postCaptor.capture());
        Post insertedPost = postCaptor.getValue();
        assertThat(insertedPost.getTitle()).isEqualTo("无标题分享");
        assertThat(insertedPost.getContent()).isEqualTo("");
        verify(postTopicService).replaceUserTopics(101L, List.of());
    }

    @Test
    void rejectsUnknownChannelKeys() {
        when(userService.requireById(7L)).thenReturn(new User());
        when(channelService.findByCode("unknown_channel")).thenReturn(null);

        CreatePostRequest request = new CreatePostRequest(
                "Title",
                "Content",
                "unknown_channel",
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> postService.createPost(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频道");
        verify(topicService, never()).resolvePublishTopics(any(), any(), any(), any(), anyLong());
    }

    private User author(Long authorId) {
        User author = new User();
        author.setId(authorId);
        author.setUsername("creator");
        author.setNickname("Creator");
        return author;
    }

    private Channel channel(String code, String name, String defaultPostType) {
        Channel channel = new Channel();
        channel.setCode(code);
        channel.setName(name);
        channel.setDefaultPostType(defaultPostType);
        channel.setStatus("ACTIVE");
        channel.setEnabled(true);
        channel.setPublishEnabled(true);
        return channel;
    }

    private Topic topic(Long id, String name, String slug) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setSlug(slug);
        topic.setStatus("ACTIVE");
        return topic;
    }
}
