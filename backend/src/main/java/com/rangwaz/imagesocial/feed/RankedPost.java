package com.rangwaz.imagesocial.feed;

import com.rangwaz.imagesocial.domain.entity.Post;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Feed 流内部排序单元，封装帖子 + 推荐理由 + 融合分数 */
public record RankedPost(Post post, String reason, int score) {

    public BigDecimal hotScore() {
        return post.getHotScore() == null ? BigDecimal.ZERO : post.getHotScore();
    }

    public LocalDateTime createdAt() {
        return post.getCreatedAt();
    }
}
