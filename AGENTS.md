# AGENTS.md

本文件供 AI 编码代理与协作者在本仓库工作时遵循。项目需求基线见 [docs/需求规格说明书.md](./docs/需求规格说明书.md)，技术设计见 [openspec/changes/add-price-diff-payment-system/design.md](./openspec/changes/add-price-diff-payment-system/design.md)。

## 项目简介

to-C 补差价系统：运营在后台创建补差价单 → 系统生成 H5 支付页与随机短码短链 → 客服转发短链 → 用户在 H5 完成支付（Mock 渠道，流程与真实渠道同构）→ 后台订单管理与数据看板。定位：可演示完整链路，核心资金链路（幂等/状态机/对账）按生产标准设计。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17、Spring Boot 3.x、MyBatis-Plus、JJWT、Maven 单体 |
| 前端 | Vue 3（组合式 API）、Vite、vue-router、axios、Element Plus、ECharts |
| 数据库 | H2（文件模式，默认）/ PostgreSQL（`pg` profile + docker-compose） |
| 测试 | JUnit 5 + Spring Boot Test（H2 内存库） |
| 规划/规格 | OpenSpec（spec-driven schema） |

## 目录结构

```
Payment/
├── AGENTS.md                    # 本文件
├── docs/需求规格说明书.md        # 需求基线（SRS）
├── openspec/                    # OpenSpec 规划与规格
│   ├── config.yaml              # schema + 项目上下文（修改技术栈/约定时同步更新）
│   ├── changes/                 # 进行中的变更（proposal/specs/design/tasks）
│   └── specs/                   # 主规格库（archive 后生成）
├── backend/                     # Spring Boot 后端（Maven）
└── frontend/                    # Vue3 前端（Vite）
```

## 编码与设计约定

### 后端

- **领域分包**：`order` / `payment` / `link` / `admin` / `dashboard`，公共代码放 `common`；不按技术层（controller/service/mapper）做顶层分包
- **支付网关抽象**：渠道相关逻辑只允许出现在 `PaymentGateway` 接口及其实现（一期 `MockGateway`）；订单业务不得直接感知渠道细节
- **统一响应体**：`{"code": 0, "message": "ok", "data": {...}}`；业务错误用错误码枚举（如 `ORDER_CLOSED`、`SIGN_INVALID`、`AMOUNT_MISMATCH`），HTTP 状态码仅用于鉴权（401）与系统级错误（5xx）
- **金额**：一律 `Long` 分存储与传输，字段命名以 `Cent` 结尾（如 `amountCent`）；禁止 float/double
- **状态机**：订单 `PENDING → SUCCESS | CLOSED`，流水 `CREATED → SUCCESS | FAILED`；流转必须收敛在领域服务内——先显式校验（业务异常），再条件 UPDATE（`WHERE status='PENDING'`，影响行数为 0 走幂等路径）
- **幂等红线**：回调处理必须依次通过 验签（HMAC-SHA256）→ 渠道交易号唯一约束 → 状态机校验 三道防线，任何一道失败不得触碰数据（幂等受理场景除外）
- **金额权威**：支付金额只从订单/流水服务端取值，客户端提交的金额参数一律忽略
- **路由分组**：`/api/admin/**`（JWT）、`/api/open/**`（短码匿名）、`/api/pay/notify/mock`（渠道回调）、`/api/mock/channel/**`（模拟渠道操作）
- **编号规则**：订单号 `BJ+yyyyMMdd+6位序列`；流水号 `TX+同规则`；短码 SecureRandom 8 位 Base62

### 前端

- Vue3 组合式 API（`<script setup>`）；页面仅三个路由：`/admin`（后台）、`/pay/:shortCode`（H5 支付页）、`/cashier/:txnNo`（模拟收银台，第三方视觉风格）
- axios 统一封装：携带 JWT（后台）、解包统一响应体、401 跳登录
- H5 与模拟收银台**不得**提交任何金额参数；订单信息一律从服务端获取

### 通用

- 代码注释、文档、提交信息用中文；标识符用英文
- 不引入 design.md 之外的重量级依赖（消息队列、Redis 等）——单实例假设是明确决策
- Non-goal 红线：不实现退款、履约触发、真实渠道、RBAC（模型已预留字段，不加逻辑）

## 常用命令

```bash
# 后端（backend/）
mvn spring-boot:run            # 启动（默认 H2 文件库，自动建表+种子账号）
mvn test                       # 全量测试（资金安全测试必须全绿）
mvn spring-boot:run -Dspring-boot.run.profiles=pg   # PostgreSQL profile

# 前端（frontend/）
npm install && npm run dev     # 启动（Vite 代理 /api 到后端）

# 数据库（可选）
docker compose up -d           # 启动 PostgreSQL

# 演示账号：admin / admin123（种子数据）
```

## OpenSpec 工作流约定

本仓库使用 OpenSpec 管理需求与规划，**行为变更必须走变更流程，不允许绕过 spec 直接改行为**：

1. **规划**：新需求/变更用 `/opsx-propose` 创建 change，生成 `proposal.md`（为什么/什么）、`specs/<capability>/spec.md`（行为契约，Requirement + Scenario）、`design.md`（技术决策）、`tasks.md`（实施清单）
2. **实现前必读**：动手前先读当前 change 的四类 artifact 与 `openspec/config.yaml` 的项目上下文；spec 是行为最终权威
3. **实施**：按 `tasks.md` 顺序执行；每完成一项立即勾选 `- [x]`（apply 工作流依赖复选框跟踪进度）；任务内含验证方式，完成前先验证
4. **校验**：`openspec validate "add-price-diff-payment-system"` 必须通过；`openspec status --change "<name>"` 查看进度
5. **归档**：实现完成且验收通过后用 `/opsx-archive` 归档，delta spec 会合并进主规格库 `openspec/specs/`
6. **修改已规划内容**：用 `/opsx-update` 修订 change 的 artifact 并保持一致，不要手改后不同步

常用命令：

```bash
openspec context --json                                # 项目上下文与根路径
openspec status --change "<name>"                      # artifact 进度
openspec instructions <artifact> --change "<name>" --json   # artifact 写作指令
openspec validate "<name>"                             # 校验 change
openspec list --specs                                  # 已归档能力清单
```
