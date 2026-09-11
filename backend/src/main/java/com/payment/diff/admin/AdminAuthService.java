package com.payment.diff.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录服务：账号密码校验（BCrypt）→ 签发 JWT。
 * 账号不存在与密码错误返回同一错误提示，不泄露账号是否存在。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        AdminUser user = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        String token = jwtService.issue(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
