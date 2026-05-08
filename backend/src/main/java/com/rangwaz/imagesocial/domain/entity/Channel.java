package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("channels")
public class Channel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private String iconUrl;
    private String coverUrl;
    private Integer sortOrder;
    private Boolean enabled;
    private String status;
    private String navGroup;
    private String defaultPostType;
    private Boolean waterfallEnabled;
    private Boolean publishEnabled;
    private Boolean recommendEnabled;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
