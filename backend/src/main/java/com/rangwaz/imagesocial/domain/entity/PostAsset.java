package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post_assets")
public class PostAsset {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String objectKey;
    private String fileUrl;
    private String fileType;
    private String thumbUrl;
    private Integer width;
    private Integer height;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
