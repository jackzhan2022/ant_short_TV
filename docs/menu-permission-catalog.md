# 菜单权限集（2026-10-24）

团队角色只配置 10 个菜单权限。拥有菜单即可使用菜单中的功能；后端业务操作统一根据菜单授权判断。团队隔离、项目成员/角色有效状态、历史项目可见范围、任务归属、Owner 转让和平台管理员限制独立校验。

## 团队菜单

以下权限类型均为 `MENU`，在团队角色权限树中按“菜单 · 创作 / 我的 / 管理”分组，可独立勾选。分组标题本身不是权限。

| 分组 | 菜单 | 权限码 | 路由 | 额外条件 |
| --- | --- | --- | --- | --- |
| 创作 | 短剧创作 | `MENU:SHORT_DRAMA_CREATION` | `/short-drama-creation` | 有效团队成员 |
| 创作 | 自由创作 | `MENU:FREE_CREATION` | `/free-creation` | 有效团队成员 |
| 创作 | 视频拆剧 | `MENU:VIDEO_DECOMPOSITION` | `/video-script-decomposition` | 有效团队成员 |
| 创作 | 剧本审核 | `MENU:SCRIPT_REVIEW` | `/script-review-library` | 有效团队成员；审核历史和任务详情使用同一菜单权限 |
| 我的 | 项目中心 | `MENU:PROJECTS` | `/projects/list` | 有效团队成员 |
| 我的 | 资产库 | `MENU:ASSET_LIBRARY` | `/asset-library` | 有效团队成员；项目资产详情使用同一菜单权限 |
| 我的 | 任务中心 | `MENU:PRODUCTION_TASKS` | `/tasks` | 有效团队成员 |
| 管理 | 剧集管理 | `MENU:DRAMA_LIBRARY` | `/drama-library` | 有效团队成员及真实平台管理员身份 |
| 管理 | 团队管理 | `MENU:TEAM_MANAGEMENT` | `/team/my` | 有效团队成员；尚未选择团队时保留创建/加入/选择团队入口 |
| 管理 | 管理后台 | `MENU:AI_MANAGEMENT` | `/ai-service-management/model-management` | 团队入口需有效团队成员；平台管理者仍按原平台权限进入 |

团队管理菜单包含角色、成员、邀请、设置和团队套餐操作。所有权转让仍需当前 Owner，不能通过分配 Owner 角色替代转让。管理后台菜单包含团队 Skill 与调用日志；平台配置仍需平台权限。

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

- V149 注册了菜单权限。V150 将原 `PROJECT:VIEW_ALL` 授权迁移到角色内部的项目访问标记，保留原项目可见范围；该标记不作为可配置权限返回。
- 原项目角色中的 `PROJECT:VIEW` 转为 `MENU:PROJECTS` 关联，仅用于确定项目成员是否可以访问该项目。项目角色不会替代团队角色授予菜单权限。
- V150 删除 `permission` 中所有非 `MENU` 记录及其团队/项目角色关联。初始化和角色保存只允许当前菜单集合，不再创建旧页面、按钮或 API 权限。
- 为防止 V149 的默认导航授权变为管理权，未具备原管理授权的历史普通角色移除团队管理和管理后台菜单。V149 后明确编辑/新建的自定义角色保留其菜单选择。
- 新团队的 Owner、Admin 默认拥有全部菜单；Member 默认拥有 7 个创作和工作区菜单，不包含团队管理、管理后台、剧集管理。Owner 始终拥有全部团队菜单。
- 多个有效团队角色的菜单取并集。角色保存后前端刷新当前团队的登录上下文；其他成员刷新页面可更新菜单，后端下一次请求即使用新授权。
- 内部操作标识仍用于描述共享接口的操作，由 `MenuAuthorization` 映射到菜单；这些标识不是可分配权限，不写入权限表，直接提交旧权限码会被拒绝。

## 发布与回退

先备份数据库和共享文件；停写后再保留权限表及角色关联的定点快照。发布后端并执行 V150，检查仅剩 10 个菜单权限、无孤立关联、项目访问标记完整，再发布前端。

V150 删除旧授权，不能只回切旧 JAR。回退必须停服务，恢复权限快照和 V150 前的迁移状态，再启动旧版本；业务数据、平台权限、团队与项目成员关系不回滚。
