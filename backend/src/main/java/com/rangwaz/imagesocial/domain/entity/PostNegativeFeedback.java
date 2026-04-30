package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post_negative_feedbacks")
public class PostNegativeFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long postId;
    private String feedbackType;
    private String reason;
    private LocalDateTime createdAt;
}
