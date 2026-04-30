package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_follows")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long followerId;
    private Long followedId;
    private LocalDateTime createdAt;
}
