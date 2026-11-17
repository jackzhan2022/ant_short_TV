# 资产详情预算调整与线上验证

2026-11-16，将 `read_asset_details` 的响应预算由 32KiB 提高至 128KiB。搜索和候选目录保持 32KiB；资产详情超过新预算时仍返回有界摘要并支持既有分页。

## 修改与验证

- `AssetCatalogService` 为详情单独定义预算，完整返回和摘要预算均使用新上限。
- 工具说明同步标注 128KiB，输入条数、权限、游标、保存和执行步数限制保持原逻辑。
- 新增 32–128KiB 详情完整返回的回归测试，先确认旧逻辑返回 SUMMARY 导致测试失败，再实施修改。
- 相关测试 29 项通过，涵盖详情完整返回、超过新预算时的摘要、分页、权限及异常传播。

## 发布

- 目录：`/opt/antv/releases/20261116-detail-budget-128k`。
- 上一版本：`/opt/antv/releases/20261113233548-b48f4c50-asset-skills`，保留用于回退。
- JAR SHA-256：`caa31f695d58a4190f70c0729a8502e4d375dbbf70551b0e5bd4d59a49c28564`，上传后核验一致。
- 比较两个 JAR，只有 AssetCatalogService（含两个嵌套类）和 ScreenplayToolConfiguration 编译产物不同；无新增/删除条目，数据库迁移及依赖逐项一致。
- 沿用上一版前端和共享 Skill。发布前执行任务及 Agent 运行数均为零；切换应用后服务 active，健康 UP。

## 真实任务重试

- 项目 90，解析任务 82；失败集为第 29 集《救过他的命，不欠他的债》，内部剧集 ID 2007。
- 发布前已尝试 5 次，最后失败为 WORKFLOW_AGENT_STEP_LIMIT，角色场景识别进度 69/70。
- 使用线上正常积分确认和重试入口提交第 6 次尝试，未手动修改数据库状态或结果。
- 新运行 6732 于北京时间 17:57:33 开始，资产详情一次返回 FULL、77,309 字节。
- 17:59:16 第 9 步 save_episode_assets 返回 saved:true，运行 SUCCESS；进度达到 70/70、0 失败，随后进入自动资产归一汇总。
- 18:02:14 资产归一汇总完成，快照 108 状态 SUCCEEDED，解析任务 82 状态 COMPLETED、错误字段为空；后续自动分镜已由系统启动。
- 本次验证证明该失败集恢复和 128KiB 工具返回生效，不将其视为后续自动分镜或图片质量验收。

## UI 预览核对

11 月 6 日可视化目录中存在 parsing-refined/parsing-polished 等解析失败设计，以及 studio-live/points-dialog-preview.html 积分弹窗设计。与当前 script.tsx 和 point-estimate.tsx 对比，这些预览布局尚未接入正式页面。本次发布只调整后端预算，未实施这些 UI 方案。
