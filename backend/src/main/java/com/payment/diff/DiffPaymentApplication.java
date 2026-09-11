package com.payment.diff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 补差价系统后端启动类。
 */
@EnableScheduling
@SpringBootApplication
public class DiffPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiffPaymentApplication.class, args);
    }
}
