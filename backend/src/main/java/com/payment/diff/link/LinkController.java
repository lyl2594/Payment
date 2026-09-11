package com.payment.diff.link;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 短链跳转端点（匿名）：GET /s/{shortCode} → 302 前端 H5 支付页。
 */
@RestController
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    @GetMapping("/s/{shortCode}")
    public void redirect(@PathVariable String shortCode, HttpServletResponse response) throws IOException {
        // 短码是否存在不在此校验：H5 页凭短码调开放 API 时再做失效判定与提示
        response.sendRedirect(linkService.h5PayUrl(shortCode));
    }
}
