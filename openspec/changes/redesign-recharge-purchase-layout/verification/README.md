# 充值购买页实施验证

初次验证：2026-10-06；发布前复验：2026-10-07。工作分支：`codex/recharge-purchase-layout`；变更前 `master` 提交：`a13b86ce0764a378c1e66e71876b988b4d1565e1`。

## 结果

- `frontend`: 商业页、计费管理和字段字典相关的 12 个测试文件、76 个测试全部通过。下单与查单服务在单元测试中均被 `vi.mock` 替代，没有真实支付请求。
- `backend`: `CommercialPackageServiceTest` 19 个、`CommercialSubscriptionLifecycleTest` 12 个测试通过，覆盖商品生效窗口、积分包权益兼容、会员一次性积分、零额度周期积分及失败重试。测试使用内存 H2 和微信支付桩。
- `backend`: `mvn -q -DskipTests package` 成功，JAR 包含 `BOOT-INF/classes/com/antshorttv/commercial/CommercialOrderService.class`，可由 Spring Boot 直接启动。
- `frontend`: `npm run lint`（Biome + TypeScript）通过。3 条现存 `requestErrorConfig.test.ts` cookie 写法提示与本变更无关。
- `frontend`: `npx antd lint ./src` 退出码 0；剩余 11 条提示均在本变更目录外。`npm run build` 成功，生成 `dist/recharge/index.html`。
- `git diff --check` 与 `node --check verification/visual-check.cjs` 通过；前者仅提示 Windows 工作区换行符转换。
- `visual-check.cjs` 对预览服务 `http://localhost:8001/recharge` 拦截全部 `/api/**` 请求，使用固定目录、账户、订单和二维码测试桩；截图与 [visual-check-results.json](visual-check-results.json) 记录 25 个场景，320/390/768/1440px 的浅色和深色页面均无页面级横向溢出。窄屏积分表允许表格内部横向滚动。
- 可视化脚本实际验证了会员四周期、积分包、仅积分包、空目录、目录错误、长名称与多权益、订单继续支付、支付过期、权益发放中至完成、键盘选卡与类别切换，以及 Escape 关闭付款后的焦点返回。浏览和选卡阶段下单次数为 0；支付场景的 POST 仅命中本地拦截器。

代表性截图：[桌面浅色](screenshots/1440-light-membership.png)、[手机浅色](screenshots/320-light-membership.png)、[手机深色](screenshots/320-dark-membership.png)、[浅色支付](screenshots/390-light-payment-expired.png)、[深色支付](screenshots/390-dark-pending.png)、[权益发放中](screenshots/390-light-fulfillment-pending.png)。其余状态截图位于 `screenshots/`。

## 契约与回退

- `/recharge` 路由保持 `layout: false` 和 `canViewCommercial`；目录、订阅、订单、积分账户及流水仍使用既有 API。创建订单仅提交所选 `packageVersionId`，金额以服务器订单响应为准。后端在创建时复核商品生效窗口及积分包权益兼容，按订单履约会员一次性积分，不需要数据库迁移。
- 生产代码没有引入原型 HTML/运行时、测试目录商品、示意价格或假二维码；未修改后台定价或权益定义。
- [rollback-master-dist.zip](rollback-master-dist.zip) 是从变更前 `master` 独立构建的可部署前端静态包，SHA-256：`5CFE5C599AD6AAF9746F44919129F18A9D53751A82DE8518B72C289FA154C059`。另存 [rollback-master-commercial-source.zip](rollback-master-commercial-source.zip) 便于核对旧版商业页源码。线上回退优先使用保留的上一版完整 release。

本地预览服务地址：`http://localhost:8001/recharge`。其 API 代理目标由 `API_PROXY_TARGET` 决定，浏览器仍需登录；确定性验证通过 `visual-check.cjs` 的 API 拦截完成。
