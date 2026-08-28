## Why

当前侧边栏按技术页面平铺，创作、个人空间、管理和商业功能边界不清；团队邀请也独立于团队管理。重排菜单并集中团队治理入口后，用户可以按工作目标快速定位功能。

## What Changes

- 新增四个菜单分组：创作、我的、管理、商业。
- 将短剧创作、视频拆剧、剧本审核归入创作；项目、风格库归入我的。
- 将团队管理、模型管理归入管理；套餐管理、AI 运维归入商业。
- 在团队管理页面新增邀请管理 Tab，复用现有收到邀请与已发邀请内容。
- 隐藏旧邀请菜单并重定向到团队管理，保留所有现有 URL 和权限控制。

## Capabilities

### New Capabilities

- `sidebar-information-architecture`: 四组侧边栏菜单结构
- `team-invitation-management-tab`: 团队管理中的邀请管理 Tab

### Modified Capabilities

## Impact

影响 `frontend/config/routes.ts`、团队管理与邀请页面组件及测试；不修改后端 API 或数据库。
