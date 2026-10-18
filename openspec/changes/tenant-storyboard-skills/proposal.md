## Why

Skill 管理目前只有全局文件，团队无法独立调整分镜创作规则，列表也缺少 Agent 与归属筛选。需要在保留素材引用和富文本输出协议的同时，为团队提供隔离的分镜规则覆盖。

## What Changes

- Skill 列表增加 Agent 下拉筛选、系统内置/租户级筛选及所属租户展示。
- 仅团队所有者或有效 ADMIN 角色可自助维护当前租户的整集分镜规划和 Seedance 视频提示词两个 Skill。
- 基础规范、素材引用保持系统只读；系统管理员不获得租户覆盖的编辑权限。
- 分镜运行按同 code 优先读取当前租户覆盖，未配置时回退系统 Skill。其他 Agent 继续使用系统 Skill。
- 支持恢复系统默认、修订冲突检查及运行快照的来源记录，保留现有分镜富文本标签、未绑定占位和素材编译协议。

## Capabilities

### New Capabilities
- `tenant-storyboard-skills`: 团队分镜 Skill 编辑、隔离、列表归属筛选与运行时优先选择。

### Modified Capabilities

无；现有全局 Skill 编辑接口及文件格式保持兼容，新增独立租户接口。

## Impact

影响 Skill 管理 API、文件 Skill 读取与 Agent 运行快照、团队权限判断及模型管理页面。新增租户 Skill 覆盖表；无需新增富文本编辑依赖，不改变分镜保存或媒体生成契约。
