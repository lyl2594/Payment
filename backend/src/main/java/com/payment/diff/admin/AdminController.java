package com.payment.diff.admin;

import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.web.AuthConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台登录与当前账号接口。
 * /api/admin/login 由 JwtAuthFilter 放行（匿名）；/api/admin/** 其余路径需有效 JWT。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(adminAuthService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, String>> me(jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.ok(Map.of("username", (String) request.getAttribute(AuthConstants.ATTR_USERNAME)));
    }
}
