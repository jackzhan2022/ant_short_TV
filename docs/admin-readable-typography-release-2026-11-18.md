# 后台字号与主题色生产发布

2026-11-18（北京时间）发布至 https://antv.aixmax.cn。

## 版本与范围

- 应用提交：`adea01f4738719e8b47d4de3c30f1a96bc5619c0`，已合并并推送远程 master。
- 租户、套餐、订单、管理后台共用样式及消息中心：列表、表头、标签、辅助文字统一使用系统字号 token（14px），保留标题和大型汇总数字层级。
- 页面自定义淡色改为主题及语义 token；辅助文字使用次要文字色；租户和消息图标统一主题色，普通标签使用中性色。
- 管理后台开关使用中号及 14px 标签，调整状态列宽。

## 验证

- 16 个相关测试文件、122 项测试全部通过。
- npm run lint（Biome + TypeScript）、Ant Design 检查、从干净且已推送提交执行的生产构建均退出 0。
- 保留现有 Biome 14 条警告、4 条提示及 Ant Design 9 条 usage 提示。
- 6 个公网页面入口与构建 HTML 哈希一致；9 个关键脚本和样式资源独立公网 SHA-256 核验通过。
- 登录态核验租户、套餐、订单、消息中心及模型服务商页面，表头与表格内容为 14px，无页面级横向溢出。租户辅助文字为 rgb(110,113,140)，符合系统次要文字色；模型服务商开关宽 61px、高 22px，标签字号 14px、显示完整。
- 浏览器初次使用了旧缓存，刷新后加载主脚本 umi.9055bf07.js，并确认新样式。没有注入临时预览 CSS。

## 发布与回退

- 当前目录：`/opt/antv/releases/20261118-adea01f4-admin-typography`。
- 前一版本：`/opt/antv/releases/20261117-wecom-customer-service`，保留用于原子回退。
- 前端包 SHA-256：`280f14443be735e5ff2fc39bafdbbf972054cd6453ec599e339c7e4461b64a3d`。
- 发布前取得部署锁、核验起始版本和发布包哈希，切换 current 软链接；验收失败自动回退。
- 保留 1000 个旧非 HTML 资源，兼容已打开的旧页面。
- 后端目录链接至上一版本，JAR 和共享环境哈希未变化。服务 active、健康 UP，MainPID 816889、NRestarts 0；未重启服务或执行数据库迁移。
- 发布清单、脚本、构建日志及回执位于本机忽略目录 `.release/admin-typography/`（构建与检查日志在 frontend/typography-*.log），服务器回执位于 `/tmp/antv-admin-typography-adea01f4/receipt.json`。
