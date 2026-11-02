# 租户成员切换登录

入口：租户管理 `/commercial-management/tenants` → 查看详情 → 全部成员 → 操作列“切换登录”。

## 使用方式

平台管理员选择成员后，填写切换原因和本人验证器验证码。未绑定验证器时，弹窗提供绑定流程。切换成功后完整刷新页面，直接进入所选租户；顶部展示当前身份，可点击“返回管理员”回到租户管理。

列表包含有效、已移除成员及账号状态。停用租户、已移除成员、停用/删除账号、本人及平台管理员账号不可切换。仅具有租户查看权限的运营人员可以查看列表，不能代登录。

代登录按目标用户实际权限执行，支持修改数据、发起任务和消耗业务所属团队的积分，不继承管理员平台权限。审计记录切换原因、实际管理员、目标用户、会话及写操作，不记录密码、验证码或请求体。

切换和返回撤销旧会话，保留原到期时间。其他标签页提交旧会话标识时提示刷新。目标账号停用、改密或升为平台管理员后停止业务访问，原管理员会话仍有效时可以返回；管理员会话或权限失效则需要重新登录。

## 发布

新增 Flyway 迁移 `backend/src/main/resources/db/migration/V172__administrator_impersonation.sql`，给 `auth_session` 增加目标用户、目标凭证版本、起始租户和查询索引。2026-11-02 已随分镜优化一起部署线上，V172 迁移、后端健康与公网资源核验通过。详见[上线记录](storyboard-impersonation-release-2026-11-02.md)。

- 开始：`POST /api/platform/tenants/{tenantId}/members/{memberId}/impersonation`，请求 `{code, reason}`。
- 返回：`POST /api/auth/impersonation/stop`，保留 CSRF 校验，服务端独立验证管理员会话，允许目标账号失效后的恢复。
- 详情新增 `members`；bootstrap 的 `session` 新增可空 `impersonation`。

## 验证记录（2026-11-02）

后端 41 个测试通过，包括实际修改租户信息、目标权限、双身份审计、验证码/CSRF 拒绝、不可用成员、返回恢复、旧会话撤销、并发活动更新及角色变化。

```powershell
mvn '-Dtest=ImpersonationControllerTest,AuthSessionServiceTest,AuthControllerTest,AuthBootstrapControllerTest,PlatformTenantControllerTest,PlatformTenantServiceTest,OperationLogServiceTest,AuthenticatorTotpTest' test
```

前端在 `.temp/impersonation-frontend-validation` 使用现有 lockfile 安装完整依赖并生成 Umi 配置。23 个相关文件与工作区 SHA256 一致。14 个测试文件、98 个测试通过，Biome 和 TypeScript 检查通过；Ant Design 检查退出码 0，保留项目已有的 9 条静态反馈 API 使用提示。

```powershell
npm test -- src/access.test.ts src/requestErrorConfig.test.ts src/services/account-team src/pages/commercial-management/tenants src/components/ImpersonationBanner src/app.test.tsx src/utils/systemMonitoring.test.ts
npm run lint
npx antd lint ./src
```

该验证目录保存 `validation-tests.log`、`validation-lint.log`、`validation-antd.log` 和 `feature-hashes.json`。后端日志位于项目根目录 `impersonation-backend-regression.log`。
