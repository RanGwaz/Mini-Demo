package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用唯一用户名 */
    private String username;

    /**
     * 对外展示的用户唯一编号（8位，字母/数字/-/_）。
     * 不暴露自增 id，防止用户规模被枚举；允许半年修改一次。
     */
    private String userNo;

    /** 最近一次修改 userNo 的时间，应用层判断距今是否满 180 天 */
    private LocalDateTime userNoUpdatedAt;

    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String backgroundUrl;
    private String bio;

    /**
     * 手机号 SHA-256(phone + salt)，唯一索引。
     * 明文手机号不落库，仅存哈希，满足隐私合规要求。
     */
    private String phoneHash;

    /**
     * 每次绑定/换绑手机时重新生成的随机盐（UUID hex），
     * 使彩虹表攻击失效。
     */
    private String phoneSalt;

    /** 最后登录时间，登录成功后异步更新，不阻塞主链路 */
    private LocalDateTime lastLoginAt;

    /** 最后登录 IP，支持 IPv6（最长 45 字符） */
    private String loginIp;

    private String roles;
    private String status;

    /** 逻辑删除：0=正常，1=已删除；MyBatis-Plus @TableLogic 自动过滤 */
    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号；MyBatis-Plus @Version 自动 CAS 更新 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
