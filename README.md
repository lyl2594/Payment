# 补差价支付系统（Diff Payment）

to-C 补差价收款系统：运营在后台创建补差价单 → 系统生成 H5 支付页与随机短码短链 → 客服把短链发给用户 → 用户在 H5 完成在线支付（Mock 模拟渠道，流程与真实渠道同构）→ 后台订单管理与数据看板。

核心资金链路（幂等 / 状态机 / 对账安全）按生产标准设计；外围功能从简，可完整演示端到端链路。

需求基线见 [docs/需求规格说明书.md](./docs/需求规格说明书.md)，技术设计见 [openspec/changes/add-price-diff-payment-system/design.md](./openspec/changes/add-price-diff-payment-system/design.md)。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17、Spring Boot 3.x、MyBatis-Plus、JJWT、Maven 单体 |
| 前端 | Vue 3（组合式 API）、Vite、vue-router、axios、Element Plus、ECharts |
| 数据库 | H2（文件模式，默认，零依赖）/ PostgreSQL（`pg` profile） |
| 测试 | JUnit 5 + Spring Boot Test（H2 内存库），50 个用例覆盖资金安全 |

## 两条命令启动

前置：本机安装 JDK 17+、Maven 3.9+、Node.js 18+。

```bash
# 1. 启动后端（backend/ 目录，默认 H2 文件库，自动建表 + 种子账号）
mvn spring-boot:run

# 2. 启动前端（frontend/ 目录，Vite 代理 /api 到 8080）
npm install && npm run dev
```

启动后：

- 运营后台：http://localhost:5173/admin （演示账号 `admin / admin123`）
- H5 支付页与模拟收银台由短链进入（建单后自动生成）

可选：PostgreSQL 模式（本机需有 PG 实例，或 `docker compose up -d`）：

```bash
# 先建库建用户（或使用 docker-compose.yml 中的默认值 diffpay/diffpay）
mvn spring-boot:run -Dspring-boot.run.profiles=pg
```

> **Windows 命令行调用 API 的编码提示**：PowerShell 5.x 的 `Invoke-RestMethod` 对字符串 body 默认按 ISO-8859-1 发送，中文会被替换为 `?` 存入数据库（有损、不可逆）。传中文请走浏览器 UI，或显式指定编码：
>
> ```powershell
> $body = [System.Text.Encoding]::UTF8.GetBytes('{"originalOrderNo":"DD1","productName":"中文商品","amountCent":1000}')
> Invoke-RestMethod -Uri "http://localhost:8080/api/admin/orders" -Method Post -Headers $h -ContentType "application/json; charset=utf-8" -Body $body
> ```

## 演示脚本（全链路走查）

1. **后台建单**：打开 `/admin` 登录 → 点「创建补差价单」→ 填写原订单号（必填）、商品名称、补差金额 → 创建成功弹窗显示订单号与支付短链（如 `http://localhost:8080/s/Ab12Cd34`）。
2. **用户支付**：新标签打开短链（自动 302 到 H5 支付页）→ 页面展示金额/商品/剩余有效期（金额从服务端下发，页面无任何金额输入）→ 点「确认支付」跳转模拟收银台。
3. **失败路径**：收银台点「模拟支付失败」→ 返回商户页显示「支付未完成」→ 点「重新支付」生成**新流水**（同一订单可多次发起支付，旧流水 FAILED 留痕）→ 收银台点「确认支付」→ 返回 H5 显示「支付成功」。
4. **后台同步**：回到 `/admin` 点「刷新」→ 指标卡（累计收款/今日收款/订单状态计数/成功率）与订单列表状态实时更新；订单详情抽屉可查看原订单信息与支付流水。
5. **超时关单（双保险演示）**：建单时把有效期调为极短（如 API 传 `expireHours=0`）→ 用户访问短链时惰性校验直接关单并显示「订单已失效」；未访问的过期订单由定时任务（每 60s 扫描）自动关闭。

## 关键设计讲解要点

- **回调幂等三道防线**（`payment/PaymentService`）：① HMAC-SHA256 验签 → ② 渠道交易号唯一约束（防重放）→ ③ 状态机前置校验 + 条件 UPDATE（`WHERE status='PENDING'`，影响行数为 0 走幂等受理）。任何一道失败不触碰数据，重复回调只入账一次。
- **超时关单双保险**（`order/OrderExpireScheduler` + `link/LinkService`）：定时任务扫描关单 + 支付/访问时惰性校验，且关单采用条件 UPDATE 防止与支付回调并发竞态。
- **支付网关抽象**（`payment/gateway/PaymentGateway`）：渠道相关逻辑只存在于网关接口及实现（一期 `MockGateway`），订单业务不感知渠道细节，可插拔替换真实微信/支付宝。
- **金额权威**：金额一律 `Long` 分存储传输（字段以 `Cent` 结尾）；H5 与收银台不提交任何金额参数，支付金额只从服务端订单/流水取值；回调金额与服务端不一致直接拒绝入账。
- **短码安全**（`link/LinkService`）：SecureRandom 8 位 Base62 随机短码（62^8 ≈ 2.2×10^14 组合），不可猜测不可遍历；订单失效短链即失效。
- **状态机收敛**：订单 `PENDING → SUCCESS | CLOSED`、流水 `CREATED → SUCCESS | FAILED`，全部流转收敛在领域服务内，先显式校验（业务异常）再条件 UPDATE。

## 常用命令

```bash
# 后端（backend/）
mvn spring-boot:run                            # 默认 H2 文件库
mvn spring-boot:run -Dspring-boot.run.profiles=pg   # PostgreSQL profile
mvn test                                       # 全量测试（资金安全测试必须全绿）

# 前端（frontend/）
npm install && npm run dev

# 数据库（可选，PostgreSQL 模式）
docker compose up -d
```

## 目录结构

```
Payment/
├── AGENTS.md                    # AI 协作约定
├── README.md                    # 本文件
├── docs/需求规格说明书.md        # 需求基线（SRS）
├── openspec/                    # OpenSpec 规划与规格（spec 是行为最终权威）
├── backend/                     # Spring Boot 后端（领域分包：order/payment/link/admin/dashboard）
├── frontend/                    # Vue3 前端（/admin 后台、/pay/:shortCode H5、/cashier/:txnNo 模拟收银台）
└── docker-compose.yml           # 可选：PostgreSQL
```

## 已知边界（Non-goal）

- 不实现退款、支付后履约触发、真实支付渠道、RBAC（数据模型已预留字段）
- 单实例假设：多实例部署时回调幂等需引入分布式锁/MQ 去重
- 短链匿名访问，不强制验证用户身份（产品选择）
