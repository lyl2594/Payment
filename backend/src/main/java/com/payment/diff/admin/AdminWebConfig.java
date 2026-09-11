package com.payment.diff.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 后台安全装配：BCrypt 编码器 + JWT 过滤器（仅拦截 /api/admin/*）。
 */
@Configuration
public class AdminWebConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(
                new JwtAuthFilter(jwtService, objectMapper));
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(10);
        return registration;
    }
}
