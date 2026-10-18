# 租户 Skill 系统模板预填发布（2026-10-18）

## 当前行为

点击“新增团队 Skill”时，实时读取当前系统“短剧分镜制作”的完整 Markdown，包含名称、描述、正文及现有素材标签和占位规则，供租户在此基础上修改。保存使用 NEW 修订条件，生成独立租户记录；后续编辑继续使用修订锁。系统 100 与租户 200 共同生效，租户创作规则优先。

模板通过 `GET /api/tenants/{tenantId}/workflow-skills/short-drama-storyboard-production/template` 读取，仅当前有效团队所有者/有效 ADMIN 可访问。普通成员仍只能查看本团队已保存记录；平台权限不自动授予租户模板访问权。读取模板不创建记录；取消新增不写库。模板加载有等待状态，失败可以重试，团队切换会丢弃旧响应。

## 验证

- 后端 2 个测试类共 9 项通过：系统最新全文、读取无写入、独立副本保存、权限隔离及既有系统/租户操作。
- 前端 2 个测试文件共 16 项通过：全文预填、NEW 保存、加载失败重试、重复点击、团队切换隔离及既有编辑/筛选行为。
- Biome、TypeScript、antd 检查和前后端生产构建通过；仅保留原有 3 条 cookie、10 条 antd 使用警告。
- 发布前活动执行/分析/Agent 任务均为 0；SQL 与 Java 迁移文件均保持相同，数据库仍为 V135。
- 线上服务正常，页面及入口/功能资源哈希匹配发布包，匿名模板/系统/租户接口均返回 401；27 份系统 Skill、租户记录和共享环境配置哈希保持不变。
- 未进行付费 AI 生成或真实租户写入；有权限的模板读取和保存流程由集成测试验证。

## 发布与恢复

- 代码提交：`974ee58a5396e75e5ea3d5fa1d46e92cd75e0cc5`，已推送 master 和 `codex/tenant-skill-system-template`。
- 发布目录：`/opt/antv/releases/20261018-tenant-skill-template-974ee58`。
- 前一目录：`/opt/antv/releases/20261018-unified-storyboard-2176c74`。
- 数据库/Skill 备份：`/opt/antv/backups/20261018-tenant-skill-template-145430`，压缩完整性、转储完成标记和 Skill 归档读取校验均通过。
- 发布结果：当前目录 `tenant-skill-template-release.json`，`success=true`。

本次仅更新应用，无数据库或 Skill 内容迁移；如需回滚，可在停止任务写入后切回前一发布目录并重启服务，无须回退数据库或覆盖共享 Skill 文件。
