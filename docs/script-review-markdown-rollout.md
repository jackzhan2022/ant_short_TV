# 剧本审核 Markdown 报告上线说明

## 开启方式

部署数据库迁移后，设置 `REVIEW_WORKFLOW_MARKDOWN_REPORTS_ENABLED=true`。QUICK 和 DEEP Agent 适配器仍需按现有环境分别启用；只有对应模式的适配器可执行时，新任务才会冻结为 `MARKDOWN`，否则继续冻结为 `STRUCTURED_JSON`。已存在任务继续使用创建时冻结的格式。

## 运行行为

- QUICK 读取可信上下文和冻结正文后直接返回 Markdown，由服务端原样保存。
- DEEP 将长剧本切成有重叠的内容单元，再按内容单元和冻结维度保存 Markdown 片段；全部成功后按固定顺序聚合。聚合按维度、根因和稳定位置去重，并保留不同引用。
- 新的 Markdown 尝试不调度候选、语义裁决或异常门禁，也不调用旧的结果写入工具。
- 空白输出失败；供应商报告截断时任务失败并保留已返回的部分文本，允许重试。

## 兼容范围

迁移仅新增可空列，不回填或修改历史数据。`result_format` 为空的历史任务按 `STRUCTURED_JSON` 读取，原有 `result_json`、问题、命中、事件、候选和语义裁决表及工具继续保留，供历史任务查看、重试和导出。

## 回滚

将 `REVIEW_WORKFLOW_MARKDOWN_REPORTS_ENABLED` 设为 `false`，随后新建的任务恢复为结构化结果。已经创建的 Markdown 任务及其重试仍按自身 `result_format` 完成，避免同一任务在重试时改变契约。新增列和历史 Markdown 报告应保留，不执行破坏性数据库回滚。

## 验证清单

在非生产环境验证 QUICK、长剧本 DEEP、失败维度重试、仅聚合重试、取消、积分结算、报告查看和 UTF-8 `.md` 下载；同时打开迁移前的结构化任务，确认问题、命中和人工处理能力不变。
