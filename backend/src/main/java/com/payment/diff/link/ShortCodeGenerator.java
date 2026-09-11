package com.payment.diff.link;

import java.security.SecureRandom;

/**
 * 短码生成器：SecureRandom 8 位 Base62（62^8 ≈ 2.18×10^14 空间，不可猜测不可遍历）。
 * 唯一性由 short_code 唯一索引兜底，冲突时由调用方重试。
 */
public final class ShortCodeGenerator {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShortCodeGenerator() {
    }

    public static String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
