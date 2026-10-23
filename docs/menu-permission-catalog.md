# 菜单权限集（2026-10-23）

本次更新菜单权限定义、权限树和前端菜单/路由访问控制。菜单授权只决定入口与对应页面是否可访问；业务操作、积分、项目成员和数据访问继续由既有后端权限校验。本次未新增按钮权限或数据范围配置。

## 团队菜单

以下权限类型均为 `MENU`，在团队角色权限树中按“菜单 · 创作 / 我的 / 管理”分组，可独立勾选。分组标题本身不是权限。

| 分组 | 菜单 | 权限码 | 路由 | 额外条件 |
| --- | --- | --- | --- | --- |
| 创作 | 短剧创作 | `MENU:SHORT_DRAMA_CREATION` | `/short-drama-creation` | 有效团队成员 |
| 创作 | 自由创作 | `MENU:FREE_CREATION` | `/free-creation` | 有效团队成员 |
| 创作 | 视频拆剧 | `MENU:VIDEO_DECOMPOSITION` | `/video-script-decomposition` | 有效团队成员及 `AI_SERVICE:USE` |
| 创作 | 剧本审核 | `MENU:SCRIPT_REVIEW` | `/script-review-library` | 有效团队成员；审核历史和任务详情使用同一菜单权限 |
| 我的 | 项目中心 | `MENU:PROJECTS` | `/projects/list` | 有效团队成员 |
| 我的 | 资产库 | `MENU:ASSET_LIBRARY` | `/asset-library` | 有效团队成员；项目资产详情使用同一菜单权限 |
| 我的 | 任务中心 | `MENU:PRODUCTION_TASKS` | `/tasks` | 有效团队成员 |
| 管理 | 剧集管理 | `MENU:DRAMA_LIBRARY` | `/drama-library` | 有效团队成员、真实平台管理员身份及 `DRAMA:VIEW` |
| 管理 | 团队管理 | `MENU:TEAM_MANAGEMENT` | `/team/my` | 有效团队成员；尚未选择团队时保留创建/加入/选择团队入口 |
| 管理 | 管理后台 | `MENU:AI_MANAGEMENT` | `/ai-service-management/model-management` | 团队入口需有效团队成员；平台管理者仍按原平台权限进入 |

团队管理内的角色、成员等功能仍受既有业务权限限制。管理后台中的平台配置需要平台权限，勾选团队菜单不会授予平台管理能力。

## 平台菜单

以下菜单复用现有平台权限，不进入团队角色的可配置权限树。

| 菜单 | 路由 | 现有平台权限 |
| --- | --- | --- |
| 套餐管理 | `/commercial-management/packages` | `PLATFORM_COMMERCIAL_ORDER_VIEW` |
| 订单管理 | `/commercial-management/orders` | `PLATFORM_COMMERCIAL_ORDER_VIEW` |
| 租户管理 | `/commercial-management/tenants` | `PLATFORM_TENANT_VIEW` |
| AI 运维 | `/ai-service-management/operations` | `PLATFORM_AI_ACCOUNTING_VIEW` |

消息中心、充值、风格库、登录注册、邀请接受页不在当前侧栏菜单中，继续使用现有访问规则。制作工作台是独立项目页面，保留项目权限校验。

## 升级和默认授权

- `V149__tenant_menu_permissions.sql` 注册 10 个菜单权限，为已有未删除角色补齐菜单授权，保留原入口可见性。停用角色仍保持停用，其菜单权限在启用前不会生效。
- 新团队的 Owner、Admin、Member 默认具有上述菜单权限；自定义角色按提交的权限集合授权。
- Owner 保持全部权限。Admin 和 Member 被取消的菜单授权不会在登录或查询角色时自动补回。
- 菜单授权采用多个有效角色的并集。保存后重新加载登录上下文（刷新页面或重新选择团队）即可更新当前会话的菜单。
- 旧 `PAGE`、`BUTTON` 权限和现有角色绑定保留；这次新增的菜单权限不代替接口操作权限，也不改变项目数据隔离。

部署时先发布后端并执行迁移，再发布前端。代码中的菜单定义位于 `RbacMenuPermissions.java`，前端判定位于 `frontend/src/access.ts`。
