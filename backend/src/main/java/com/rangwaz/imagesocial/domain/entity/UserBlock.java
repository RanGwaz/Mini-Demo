package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_blocks")
public class UserBlock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blockedUserId;
    private LocalDateTime createdAt;
}
