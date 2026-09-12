# 剧本审核 Workflow Agent 运行说明

## 当前唯一契约

Agent 管理中的 `script-review` 用于全部 QUICK/DEEP 任务。输入冻结为独立审核剧本版本、范围和维度；只能使用 `read_review_context`、`read_review_content` 可信读取工具，聚合不开放工具。

QUICK 直接返回 Markdown；DEEP 按冻结维度/范围生成 Markdown 单元，全部成功后按顺序聚合。服务端保存非空、未截断的最终报告；没有结构化问题、候选、语义裁决或终态保存工具。空白或截断输出失败，部分输出仅用于诊断。

`review_unit_result` 保存 Markdown 片段，必须保留。成功单元复用、聚合恢复、取消、Run 日志和积分结算继续生效。只有失败单元重试与全部重新生成的区别，不存在新旧链路选择。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `REVIEW_WORKFLOW_QUICK_SAFE_CHARACTERS` | 50000 | QUICK 完整可信读取上限 |
| `REVIEW_WORKFLOW_DEEP_UNIT_CHARACTERS` | 24000 | DEEP 单元字符上限 |
| `REVIEW_WORKFLOW_DEEP_UNIT_OVERLAP` | 1200 | 单元重叠字符数 |
| `REVIEW_WORKFLOW_DEEP_MAX_CONCURRENCY` | 3 | 并发预算 |

启动自动幂等初始化缺省 Agent/文件 Skill；保留管理员当前配置。没有 bootstrap、QUICK、DEEP、缓存观测或维度编排启用开关。

## 升级与恢复

按 `openspec/changes/remove-legacy-ai-workflow-paths/data-cleanup-manifest.md` 核对目标环境，停止旧进程、释放未完成预扣、保留恢复快照，再执行后续迁移并协调启动。验证可信范围、Markdown 报告、失败单元/聚合重试和取消。

恢复必须使用一致快照与匹配程序，不通过旧直连回退。以下历史记录仅保留原验证证据，不能作为当前契约或本次验证通过证明。

## 2026-08-31 上线验证记录

- 集成测试环境已顺序覆盖 QUICK、DEEP、失败与取消路径；Agent bootstrap、16 个 Skill 和 6 个工具的注册契约均通过。
- 生产“Agent（新）”已确认 `script-review` 为启用状态，模型为 `deepseek-v4-flash`，最大步骤数为 20，页面可见 16 个 Skill 和 6 个工具。
- 生产 QUICK 第 1 轮完成；DEEP 第 3 轮完成 1/1 单元及聚合，保留 1 份单元候选，生成 1 个正式问题和 2 个正式命中，两个 Agent Run 均成功。
- 回滚演练期间关闭 QUICK/DEEP 开关，新建 QUICK 第 4 轮确认走旧版直连路径，且既有 DEEP 报告、候选、Agent 审计和正式结果保持可读。演练后两个开关已恢复开启。
