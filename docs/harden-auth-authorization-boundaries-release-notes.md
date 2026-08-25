# 认证与授权边界加固发布说明

## 变更概览

本次发布将浏览器认证切换为服务端可撤销会话，拆分平台、租户和项目权限边界，使用请求级 `X-Tenant-Id` 与统一 bootstrap 加载登录上下文，并删除组织及项目数据范围模型。旧认证、权限和当前租户接口不再提供。

## 上线前必须完成

1. **备份数据库，重点确认组织数据已保留。** Flyway `V33__remove_organization_and_project_data_scope.sql` 会删除 `organization_member`、`organization`，以及项目、项目成员和项目角色中的组织/数据范围列。备份必须在新版本首次启动、Flyway 执行 V33 之前完成，并验证备份可恢复。
2. **配置稳定的会话保护密钥。** 必须提供 `AUTH_TOKEN_PEPPER`，长度至少 32 个字符；生产环境建议使用密码学安全随机生成的至少 32 字节密钥，并通过密钥管理系统注入。缺失或过短会导致应用启动失败。不要在普通重启或横向扩容时改变该值，所有实例必须使用同一值；轮换会使已有会话立即失效。
3. **配置初始平台管理员。** 将 `PLATFORM_INITIAL_ADMIN_MOBILE` 设置为指定用户手机号。若用户已存在，应用启动时幂等分配 `PLATFORM_ADMIN`；若用户尚不存在，在该手机号注册或登录后分配。确认平台权限生效后可移除该环境变量，已有角色分配不会被移除。租户 Owner/Admin 不会自动获得平台权限。
4. **确认 HTTPS、Cookie 和 CSRF 部署链路。** 生产默认 `AUTH_SESSION_COOKIE_SECURE=true`，必须通过 HTTPS 访问。反向代理需保留 `Set-Cookie`、Cookie 和 `X-XSRF-TOKEN` 请求头；SPA 与 API 应保持同源部署。若必须跨站部署，需要单独评估并调整 SameSite、CORS、凭证和 CSRF 策略，不能只关闭 Secure 或 CSRF。

## 用户与客户端影响

- 发布切换后，旧 Bearer Token 和旧浏览器登录态无法迁移，所有用户需要重新登录。
- 前端和受支持客户端必须改用 HttpOnly 会话 Cookie；写请求同时携带服务端签发的 CSRF Cookie 对应的 `X-XSRF-TOKEN` 请求头。
- `/api/currentUser`、`/api/login/account`、`/api/login/outLogin`、`/api/user/me`、`/api/auth/permissions` 和 `/api/tenants/current` 已移除。登录上下文统一从 `GET /api/auth/bootstrap` 获取，租户请求显式携带 `X-Tenant-Id`。
- 脚本审核导入可先不关联主项目。未绑定草稿仅创建者或租户级项目管理员可访问；绑定后立即按主项目权限控制，正常流程不支持重新绑定。

## 发布与验证顺序

1. 停止旧版本写流量并完成、验证数据库备份。
2. 配置 `AUTH_TOKEN_PEPPER`、`PLATFORM_INITIAL_ADMIN_MOBILE` 和 HTTPS/Cookie 相关环境变量。
3. 部署后端并确认 Flyway 已执行至 V34，再部署匹配的新前端。
4. 使用初始平台管理员验证无租户上下文的平台 API；再分别验证租户切换、普通项目成员项目列表、项目级 403、登录、退出和 CSRF 写请求。
5. 通知用户重新登录，并监控 401/403、会话表增长、CSRF 拒绝和数据库连接负载。

## 回滚边界

- 在 V33 执行前，新增的会话和平台 RBAC 表属于可保留的加法变更，旧应用可回滚并忽略这些表。
- V33 执行后，组织表和相关列已经物理删除。此时仅回滚应用代码不足以恢复旧版本，必须停止写入并恢复上线前数据库备份；恢复会丢失备份时间点之后的新数据。
- 因此，V33 是本次发布的不可逆审批边界。没有已验证的数据库备份，不得执行生产迁移。
