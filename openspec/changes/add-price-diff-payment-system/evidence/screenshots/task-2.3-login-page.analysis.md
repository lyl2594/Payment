# task-2.3-login-page.png 截图分析

- **验证目标**：未登录访问 /admin 应被路由守卫重定向到 /login，并展示登录页（任务 2.3：登录页 + 路由守卫）。
- **实际观察**：
  - 浏览器实测：打开 http://localhost:5173/admin 后最终 URL 变为 http://localhost:5173/login，页面渲染出「补差价运营后台」登录卡片（账号/密码输入框 + 登录按钮），与本截图一致。
  - 已登录（存在 token）访问 /login 会被守卫反向重定向回 /admin（浏览器 evaluate 实测最终 URL 为 /admin）。
- **是否符合预期**：符合 spec admin-auth 与任务 2.3 的路由守卫要求。
