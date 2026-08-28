## Context

当前模型管理容器在 `frontend/src/pages/ai-service-management/model-management/index.tsx` 中按权限渲染 Provider、Model、Logs 三个 tab。Agent 页面 `frontend/src/pages/ai-service-management/agents/index.tsx` 自带 Agent/Skill 两个 tab及详情抽屉，并由独立 `/agents` 路由暴露。

## Goals / Non-Goals

**Goals:**

- 在同一模型管理容器中提供五个同级 tab。
- 保持每个 tab 的现有权限过滤、数据请求和详情交互。
- 消除独立 Agent 菜单，同时兼容旧地址。

**Non-Goals:**

- 不改后端 API、权限码、Agent/Skill 数据模型或业务逻辑。
- 不调整 Provider、Model、Logs 的既有表格功能。

## Decisions

### Extract Agent and Skill tab content

将 Agent 页面中的表格列定义、详情抽屉和两个 tab 内容拆为可复用导出组件；模型管理容器直接注册 `agents`、`skills` 两个 tab。这样避免嵌套 Tabs，并让两个新 tab与现有三个 tab保持一致的权限和切换行为。

### Preserve legacy route as hidden redirect

将 `/ai-service-management/agents` 改为隐藏路由并重定向到 `/ai-service-management/model-management`。旧链接仍可打开统一工作区，具体 tab 不通过 URL 持久化，避免引入新的路由状态协议。

### Permission-aware tab construction

Agent 与 Skill tab均使用 `canViewBuiltInAiAgents` 权限；模型管理容器继续按现有三个权限构造 tab，并以第一个授权 tab作为默认值。

## Risks / Trade-offs

- [Risk] 直接访问旧 Agent 链接后不会自动定位到 Agent tab。→ 保持旧地址可用并统一进入模型管理工作区；后续如需要可再增加 tab URL 状态。
- [Risk] 组件拆分造成测试 mock 需要调整。→ 先以现有 Agent 页面测试覆盖拆分后的导出内容，再增加模型管理容器的同级 tab 断言。
- [Risk] 用户无任何 tab 权限时页面为空。→ 沿用当前模型管理容器的空渲染行为，并由路由访问权限阻止无权限用户进入。

## Migration Plan

1. 发布前端组件拆分、模型管理五 tab 和旧 Agent 隐藏重定向。
2. 验证各权限组合下 tab 可见性，以及 Agent/Skill 列表、详情和 Prompt 预览。
3. 验证旧 `/agents`、`/providers`、`/models`、`/billing`、`/logs` 链接可访问。
4. 回滚时恢复上一版本前端路由和页面组件，不涉及数据库迁移。

## Open Questions

无。
