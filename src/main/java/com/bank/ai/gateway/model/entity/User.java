package com.bank.ai.gateway.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * @since 1.0.0
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码哈希（bcrypt） */
    private String passwordHash;

    /** 角色：ADMIN / USER */
    private String role;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 登录失败次数 */
    private Integer loginFailCount;

    /** 锁定截止时间 */
    private LocalDateTime lockedUntil;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
