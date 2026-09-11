package com.payment.diff.common.web;

import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 1.3 验证：全局异常处理器的错误码映射。
 * 约定：业务错误 HTTP 200 + 业务码；鉴权失败 401；参数错误 200+10001；系统异常 500。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTest.ThrowController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class ThrowController {

        @GetMapping("/test/business")
        public String business() {
            throw new BusinessException(ErrorCode.ORDER_CLOSED);
        }

        @GetMapping("/test/unauthorized")
        public String unauthorized() {
            throw new UnauthorizedException();
        }

        @PostMapping("/test/valid")
        public String valid(@Valid @RequestBody SampleBody body) {
            return "ok";
        }

        @GetMapping("/test/boom")
        public String boom() {
            throw new RuntimeException("boom");
        }
    }

    static class SampleBody {
        @NotBlank(message = "不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void businessErrorShouldReturn200WithBizCode() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.ORDER_CLOSED.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ORDER_CLOSED.getMessage()));
    }

    @Test
    void unauthorizedShouldReturn401() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void invalidBodyShouldReturnParamInvalid() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    void unexpectedErrorShouldReturn500() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()));
    }
}
