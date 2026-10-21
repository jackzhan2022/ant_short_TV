# 管理员给租户发放套餐或积分包

## 使用流程

商业化管理 → 租户管理 → 目标租户行的“添加套餐/积分包”。仅 PLATFORM_ADMIN 可见并调用接口，租户管理员及仅有租户查看/试用分配权限的账号均不能使用。

首次使用先输入当前登录密码，通过后使用验证器扫描二维码或输入密钥，再输入六位动态验证码完成绑定。绑定信息十分钟内有效。绑定成功使用的验证码已经消费，需等待新的验证码再发放。

选择会员套餐或积分包、选择已发布且当前可用的商品版本，填写最多 500 字的发放原因，输入本人验证器六位验证码并提交。停用租户不可发放。成功后刷新租户列表及已打开的详情。

- 积分包：一次性积分入账，不改变会员套餐。
- 同一版本的会员套餐：延长有效期，按现有规则继续周期积分发放。
- 不同版本的会员套餐：立即切换，原订阅剩余周期积分仍按原计划发放。提交前明确提示这一结果。
- 没有周期积分的会员套餐：开通会员权益，不创建周期积分任务。
- 免费人工发放，不生成支付单，也不计入付费订单统计。

## 权限与一致性

前端根据平台角色显示入口，后端每个入口独立调用平台管理员校验。操作人取自登录会话，不信任客户端传入的账号。所有写请求沿用项目 CSRF 防护。

验证器使用 SHA1、六位数字、30 秒周期的 TOTP，容忍前后一个时间步。密钥复用项目现有 AiSecretCodec 加密存储，依赖已有 ai.secret-key 配置。正式密钥不通过查询接口返回；绑定响应禁止缓存。密码、密钥和验证码不写入业务审计日志。

验证失败五次锁定十五分钟。失败计数和验证码消费使用独立事务和用户行锁，保证多实例/并发下无法通过重试或发放回滚重用验证码。重新生成待绑定密钥不会清空失败计数。本次不提供解除绑定或遗失验证器的自助重置入口。

人工发放记录保存租户、操作人、商品版本和权益快照、原因、请求标识、状态及订阅关联。积分入账、订阅变更、发放记录与成功审计在同一个事务中完成；中途失败整笔回滚。失败后验证码仍已消费，需要新的验证码。

同一操作人、同一请求标识及相同内容重试时，返回既有结果，不再发放。重试仍校验登录状态和平台管理员角色。前端网络失败后保留请求标识；更改租户、商品或原因视为新操作。

## 接口及文件

- GET /api/platform/admin-authenticator：本人绑定状态。
- POST /api/platform/admin-authenticator/enrollment：验证密码并生成待绑定密钥。
- POST /api/platform/admin-authenticator/confirmation：验证动态码并确认绑定。
- GET /api/platform/tenants/grant-options：管理员可发放商品。
- POST /api/platform/tenants/{tenantId}/package-grants：人工发放。

数据库迁移：backend/src/main/resources/db/migration/V144__admin_tenant_package_grants.sql。

新增 admin_authenticator、admin_tenant_package_grant；订阅增加人工发放来源，原支付订单来源允许为空；权益记录增加人工发放来源。实际支付仍使用原订单来源。迁移已通过测试数据库验证，运行环境需随代码发布执行 Flyway 迁移。

核心实现：backend/src/main/java/com/antshorttv/adminauth/、commercial/AdminTenantGrantService.java、commercial/CommercialBenefitFulfillmentService.java。
前端：frontend/src/pages/commercial-management/tenants/GrantPackageModal.tsx、AuthenticatorBinding.tsx、grantService.ts。

## 验证

后端测试覆盖：RFC 验证码向量、时间窗、绑定密码/验证码、密钥不重复暴露、绑定超时、权限限制、CSRF、撤销管理员后的重试、失败锁定、并发重放、幂等、租户及商品状态、部分权益失败回滚、会员续期及切换、后续周期积分、订阅来源和无周期积分套餐。

前端测试覆盖：平台角色权限、入口显示、绑定流程、发放表单、会员切换提示、积分包选择、校验失败和网络重试标识。保留原有租户管理及权限测试。

验证命令：
```powershell
# backend 目录
mvn -q '-Dtest=AuthenticatorTotpTest,AdminTenantGrantControllerTest,CommercialSubscriptionLifecycleTest,PlatformTenantControllerTest,CommercialControllerPermissionTest' test
# frontend 目录
npm test -- src/pages/commercial-management/tenants src/access.test.ts
npm run lint
npx antd lint ./src
```

验证结果：后端上述 5 个测试类共 37 项全部通过；前端租户管理及权限测试共 39 项全部通过。npm run lint（Biome + TypeScript）通过，存在原有的 3 条 document.cookie 提示；antd 检查通过，存在原有的 10 条静态反馈 API 提示，本次新增组件未产生此类提示。

测试结果记录在 outputs/admin-grant/。未对运行环境执行部署或真实租户发放。
