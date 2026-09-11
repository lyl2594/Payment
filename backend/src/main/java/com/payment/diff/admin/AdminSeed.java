package com.payment.diff.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 种子管理员：admin / admin123（BCrypt 摘要）。
 * 幂等：已存在同名账号则跳过，重启不重复插入。
 */
@Component
@RequiredArgsConstructor
public class AdminSeed implements ApplicationRunner {

    private static final String SEED_USERNAME = "admin";
    private static final String SEED_PASSWORD_RAW = "admin123";

    private final AdminUserMapper adminUserMapper;

    @Override
    public void run(ApplicationArguments args) {
        Long count = adminUserMapper.selectCount(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, SEED_USERNAME));
        if (count != null && count > 0) {
            return;
        }
        AdminUser user = new AdminUser();
        user.setUsername(SEED_USERNAME);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(SEED_PASSWORD_RAW));
        user.setDisplayName("运营管理员");
        user.setRole("ADMIN");
        adminUserMapper.insert(user);
    }
}