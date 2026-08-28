## Why

模型服务商、大模型、调用日志与内置 Agent/Skill 都属于平台 AI 管理能力，但当前 Agent/Skill 位于独立菜单，用户需要在两个入口间切换，导航层级不一致。将它们合并到模型管理页可以形成统一的 AI 管理工作区，并保持现有数据查询和详情能力。

## What Changes

- 在模型管理页现有“模型服务商 / AI 大模型 / 调用日志”之后新增同级“Agent 管理 / Skill 管理”两个 tab。
- 复用现有 Agent、Skill 列表、详情抽屉和 Prompt 预览能力。
- **BREAKING** 移除独立的 Agent 菜单入口；旧 `/ai-service-management/agents` 地址重定向到模型管理页。
- 按现有权限控制五个 tab，未授权 tab 不展示。
- 保留 Provider、Model、Billing、Logs 等旧链接的兼容重定向行为。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `model-management-navigation`: 模型管理工作区新增 Agent 管理和 Skill 管理同级 tab，并收敛独立 Agent 路由。

## Impact

- 前端模型管理容器、Agent 页面组件拆分与路由配置。
- AI 管理路由单元测试、模型管理页面测试和 Agent/Skill 页面测试。
- 不修改 Agent/Skill API、数据结构、权限定义或后端服务。
