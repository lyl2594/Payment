# Tasks: 补差价系统实施

依赖顺序执行；每个任务含完成验证方式。设计依据见 [design.md](./design.md)，行为验收标准见各 capability spec。

## 1. 工程脚手架与基础设施

- [x] 1.1 初始化 `backend/` Maven 工程：Java 17 + Spring Boot 3.x，引入 web/validation/mybatis-plus/h2/jjwt/lombok 依赖，包结构按领域分包（order/payment/link/admin/dashboard/common）。验证：`mvn spring-boot:run` 启动成功
- [x] 1.2 初始化 `frontend/` Vite + Vue3 工程：vue-router、axios、Element Plus、ECharts，配置 dev 代理转发 `/api` 到后端，页面骨架三路由（/admin、/pay/:shortCode、/cashier/:txnNo）。验证：`npm run dev` 三个路由可空页访问
- [x] 1.3 实现统一响应体 `{code,message,data}`、业务错误码枚举、全局异常处理器、健康检查接口。验证：curl 健康接口返回约定格式
- [x] 1.4 编写可移植 DDL（admin_user/diff_order/payment_txn，含 channel_txn_no 与 short_code 唯一索引、退款/履约预留列）+ H2 文件库初始化 + 种子管理员（admin/admin123, BCrypt）+ PostgreSQL profile 与 docker-compose.yml。验证：默认 profile 与 `pg` profile 均启动建表成功

## 2. admin-auth：后台登录鉴权（spec: admin-auth）

- [x] 2.1 实现登录接口：账号密码校验（BCrypt）、JWT 签发与解析工具。验证：curl 正确凭据返回 token，错误密码返回统一错误（spec 场景：正确登录/错误密码）
- [x] 2.2 实现 JWT 过滤器保护 `/api/admin/**`：无 token/无效/过期返回 401，不触达业务。验证：curl 无 token 调管理接口被拒（spec 场景：鉴权三例）
- [x] 2.3 前端登录页 + token 存储 + 路由守卫 + 401 自动跳登录。验证：浏览器登录成功进入后台，未登录直接访问 /admin 被重定向

## 3. diff-order-management：订单领域（spec: diff-order-management）

- [x] 3.1 订单实体/Mapper/状态枚举与状态机守卫方法（仅 PENDING 可流转）、订单号生成（BJ+日期+序列）。验证：单测——非法流转（已支付再支付、已关闭入账）抛业务异常且数据不变（spec 场景：非法流转被拒绝）
- [x] 3.2 创建订单接口：原订单号必填（存储+普通索引，同一原订单号可重复建单）、商品名称必填、金额（>0，单位分）校验，选填原订单金额/原商品名称/名额说明/备注，有效期默认 48h 可调、记录 created_by、生成短链并返回订单号+短链。验证：curl 建单成功且关联原订单号；单测——缺原订单号被拒、金额≤0 与缺必填被拒、同一原订单号可多笔建单（spec 场景：创建订单五例）
- [x] 3.3 订单分页列表（状态/关键词/原订单号/时间筛选）与详情接口（含原订单信息与全部支付流水）。验证：curl 按原订单号筛选返回该原订单下全部补差价单；详情含原订单信息与流水（spec 场景：列表与详情三例）
- [x] 3.4 手动关单接口：仅 PENDING 可关，关闭后短链支付不可用。验证：单测——关闭待支付成功、关闭已支付被拒（spec 场景：手动关单两例）
- [x] 3.5 超时关单：@Scheduled 每分钟扫描过期 PENDING 订单置 CLOSED；回调入账前惰性校验 expire_at，超时订单拒绝入账并关单。验证：单测——构造过期订单被扫描关闭；过期订单迟到成功回调被拒（spec 场景：双保险两例）

## 4. short-link-access：短链（spec: short-link-access）

- [x] 4.1 SecureRandom 8 位 Base62 短码生成器 + 唯一索引冲突重试。验证：单测——批量生成无重复、字符集符合 Base62（spec 场景：短码不可枚举）
- [x] 4.2 短链跳转端点 `GET /s/{shortCode}` 302 到前端 H5 路由。验证：curl -I 返回 302 且 Location 正确
- [x] 4.3 开放 API 凭短码换取订单信息（商品/名额说明/金额/状态/剩余有效期，金额服务端下发）。验证：待支付订单返回完整信息；已关闭订单返回失效提示（spec 场景：换取订单信息两例）

## 5. h5-payment + mock-payment-channel：支付闭环（spec: h5-payment、mock-payment-channel）

- [x] 5.1 定义 PaymentGateway 接口（prepay/verifyAndParse）+ MockGateway 实现：prepay 生成收银台 URL 与签名参数。验证：单测 prepay 返回可用 URL 结构（spec 场景：预下单）
- [x] 5.2 发起支付接口：校验订单 PENDING 且未超时 → 创建 payment_txn(CREATED) → 网关预下单返回收银台地址；忽略客户端提交的任何金额参数。验证：单测——发起支付生成新流水；已关闭订单被拒；附加篡改金额不生效（spec 场景：发起支付/篡改金额）
- [x] 5.3 模拟渠道端点：`/api/mock/channel/pay` 接收收银台用户操作，生成 channel_txn_no、组装回调报文 + HMAC-SHA256 签名，RestClient 回环调用回调端点模拟第三方异步通知。验证：收银台操作后产生 HTTP 回调日志
- [x] 5.4 回调端点 `/api/pay/notify/mock`：验签 → 状态机前置校验（仅非终态流水可更新）→ 金额一致性校验 → 条件 UPDATE 回写流水与订单（PENDING→SUCCESS）；channel_txn_no 唯一约束兜底，重复回调返回受理成功不重复入账。验证：单测——同一成功回调投递两次仅一次入账且第二次返回 SUCCESS；伪造签名拒绝且数据不变；金额不一致拒绝并记录（spec 场景：验签/幂等/金额一致性/回写四组）
- [x] 5.5 流水状态查询接口（凭 txn_no，供 H5 轮询，含最终态与失败原因）。验证：curl 查询支付后流水状态正确
- [x] 5.6 前端 H5 支付页 `/pay/:shortCode`：订单信息展示、失效态页、支付按钮、结果页（成功/失败+重新支付）、2s 轮询至终态。验证：浏览器完整走通 发起→收银台→成功结果页；失败后重试生成新流水（spec 场景：H5 三组）
- [x] 5.7 前端模拟收银台 `/cashier/:txnNo`：第三方风格 UI（流水号+金额）、"确认支付/模拟失败"按钮。验证：两按钮分别触发成功/失败回调链路（spec 场景：模拟收银台两例）

## 6. payment-dashboard：看板（spec: payment-dashboard）

- [x] 6.1 汇总指标接口：累计收款、今日收款、订单总数与各状态计数、支付成功率。验证：curl 与库内数据核对一致；空库返回零值不报错（spec 场景：汇总两例）
- [x] 6.2 近 N 日（默认 7）趋势接口：每日收款金额与成交订单数。验证：curl 按日聚合正确，无数据日补零（spec 场景：趋势）
- [x] 6.3 前端后台管理页：指标卡 + ECharts 趋势图 + 订单列表（含原订单号筛选/分页）+ 建单表单（原订单号必填）+ 详情抽屉（含原订单信息与流水）+ 关单操作。验证：浏览器完成一次建单→列表可见→看板数据同步变化（spec 场景：列表/看板）

## 7. 端到端验证与交付

- [x] 7.1 后端全量测试通过：`mvn test` 全绿（覆盖幂等/状态机/关单/短码四类资金安全测试）。验证：构建命令退出码 0
- [x] 7.2 全链路演示走查：后台建单→复制短链→H5 打开→支付失败→重新支付→成功→看板数据更新；补齐超时关单演示（将演示订单有效期调短）。验证：全流程人工走查通过并记录演示脚本
- [x] 7.3 PostgreSQL profile 实测：`docker compose up -d` 后以 pg profile 启动并跑通核心链路。验证：PG 库中三表有数据且回调入账成功（本机无 Docker，用常驻 PG16 服务等价实测：建库清表→pg profile 启动→登录/建单/短链/支付/回调全链路→psql 确认三表数据与入账 SUCCESS）
- [x] 7.4 编写 README：两条命令启动说明、演示脚本（含讲解要点：三道防线/双保险/网关抽象/短码设计）。验证：按 README 从零启动并完成一次演示
