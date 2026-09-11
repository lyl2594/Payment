package com.payment.diff.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 2.1 验证（spec: admin-auth「账号密码登录」）：
 * 1. 正确凭据 → 签发 token 与账号信息；
 * 2. 密码错误 → 统一"账号或密码错误"，不签发令牌；
 * 3. 账号不存在 → 同一提示，不泄露账号存在性。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLoginTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"%s\"}";

    @Test
    void correctCredentialsShouldReturnToken() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType("application/json")
                        .content(LOGIN_BODY.formatted("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordShouldReturnUnifiedError() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType("application/json")
                        .content(LOGIN_BODY.formatted("admin", "wrong-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(24001))
                .andExpect(jsonPath("$.message").value("账号或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownUserShouldReturnSameError() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType("application/json")
                        .content(LOGIN_BODY.formatted("no-such-user", "whatever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(24001))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }
}
