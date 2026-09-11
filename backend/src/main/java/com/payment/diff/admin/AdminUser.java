package com.payment.diff.admin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员。role 为 RBAC 预留字段（本期不加权限逻辑）。
 */
@Data
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 摘要 */
    private String passwordHash;

    private String displayName;

    private String role;

    private LocalDateTime createdAt;
}
