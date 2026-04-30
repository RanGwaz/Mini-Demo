package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post_comments")
public class PostComment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long postId;
    private Long parentCommentId;
    private String content;
    private LocalDateTime createdAt;
}
