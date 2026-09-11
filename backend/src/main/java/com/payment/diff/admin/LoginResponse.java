package com.payment.diff.admin;

/** 登录响应：访问令牌 + 账号信息 */
public record LoginResponse(String token, String username, String displayName, String role) {
}
