package com.payment.diff.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.exception.ErrorCode;
import com.payment.diff.common.web.AuthConstants;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理接口 JWT 鉴权过滤器（仅作用于 /api/admin/*，登录路径放行）。
 * 注意：过滤器异常不经过 @RestControllerAdvice，401 必须直写响应体。
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String LOGIN_PATH = "/api/admin/login";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (LOGIN_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            reject(response);
            return;
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            String username = jwtService.parseUsername(token);
            request.setAttribute(AuthConstants.ATTR_USERNAME, username);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            // 无 token/无效/过期（ExpiredJwtException 为 JwtException 子类）统一 401，不触达业务
            reject(response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage())));
    }
}
