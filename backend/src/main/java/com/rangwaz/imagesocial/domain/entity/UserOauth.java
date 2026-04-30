package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 第三方 OAuth 绑定表。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>独立表，与 users 表解耦；一个账号可绑定多个第三方平台</li>
 *   <li>无外键约束，user_id 关联一致性由应用层保证</li>
 *   <li>(provider, open_id) 联合唯一索引防止重复绑定</li>
 * </ul>
 */
@Data
@TableName("user_oauth")
public class UserOauth {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 users.id，无数据库外键 */
    private Long userId;

    /**
     * 第三方平台标识，枚举值示例：
     * wechat / github / google / apple / weibo
     */
    private String provider;

    /** 第三方平台用户唯一标识（微信 openid / Google sub 等） */
    private String openId;

    /** 微信 union_id 等跨应用唯一 ID，其他平台可为 NULL */
    private String unionId;

    /** 第三方 access_token，建议加密后存储，可为 NULL */
    private String accessToken;

    /** access_token 过期时间 */
    private LocalDateTime expiresAt;

    /** 第三方返回的原始 JSON 信息快照（头像/昵称等），便于回填 */
    private String rawInfo;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
