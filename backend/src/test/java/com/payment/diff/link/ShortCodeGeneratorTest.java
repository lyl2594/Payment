package com.payment.diff.link;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 4.1 验证（spec: short-link-access「短码不可枚举」）：
 * 批量生成无重复、长度 8、字符集严格 Base62。
 */
class ShortCodeGeneratorTest {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @Test
    void batchGenerationShouldHaveNoDuplicate() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50_000; i++) {
            codes.add(ShortCodeGenerator.next());
        }
        assertThat(codes).hasSize(50_000);
    }

    @Test
    void codeShouldBeBase62AndFixedLength() {
        for (int i = 0; i < 1_000; i++) {
            String code = ShortCodeGenerator.next();
            assertThat(code).hasSize(8).matches("[" + BASE62 + "]{8}");
        }
    }
}
