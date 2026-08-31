# 剧本审核 Workflow Agent 上线说明

## 能力与数据归属

- 公共 Agent code 为 `script-review`，启动时注册到“Agent（新）”。Agent 定义持有 2 个公共 Skill、13 个审核维度 Skill、1 个跨单元综合 Skill，以及 6 个审核工具的最大授权集合。
- 每次运行由服务端按所选维度和阶段冻结更小的 Skill/工具集合。客户端和模型都不能传入 Skill code、任务 ID、版本 ID、快照 ID 或单元 ID。
- QUICK 使用一次 Agent Run；DEEP 使用冻结单元的子 Run，全部候选成功后再启动一次聚合 Run。
- `review_unit_result` 只保存当前快照单元的候选文档。只有 `save_review_result` 能在一个事务中写入 `review_task.result_json`、`review_issue`、`review_issue_hit`、`review_issue_event` 和任务完成态。
- Agent 默认最大步骤数为 `20`，为可信读取、分页、纠错和最终保存预留足够预算。
- 既有审核轮次不迁移为候选数据，原问题、修复、导出、版本历史和回滚读取保持兼容。

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `AI_WORKFLOW_REVIEW_BOOTSTRAP_ENABLED` | `true` | 注册 Agent 定义；不代表业务切流 |
| `AI_WORKFLOW_REVIEW_QUICK_ENABLED` | `false` | 新 QUICK 任务切到 Workflow Agent |
| `AI_WORKFLOW_REVIEW_DEEP_ENABLED` | `false` | 新 DEEP 任务切到单元扇出与聚合 |
| `REVIEW_WORKFLOW_QUICK_SAFE_CHARACTERS` | `50000` | QUICK 完整可信读取上限 |
| `REVIEW_WORKFLOW_DEEP_UNIT_CHARACTERS` | `24000` | DEEP 单元字符上限 |
| `REVIEW_WORKFLOW_DEEP_UNIT_OVERLAP` | `1200` | 相邻单元重叠字符数 |
| `REVIEW_WORKFLOW_DEEP_MAX_CONCURRENCY` | `3` | 快照记录的最大并发预算 |

## 上线顺序

1. 执行 V84 迁移，保持 QUICK/DEEP 两个切流开关关闭；确认 Agent、Skills 和 6 个工具在管理页可见。
2. 非生产环境开启 QUICK，验证 ALL、EPISODES、SCENES、单维度、多维度、历史问题匹配、账单和取消。
3. 生产小流量开启 QUICK，监控工具校验失败、重复保存、过大范围、延迟和调用量。
4. 非生产开启 DEEP，验证无集标题剧本、失败单元重试、聚合重试、取消和服务重启后的进度恢复。
5. 再逐步开启生产 DEEP。

## 回滚

关闭 `AI_WORKFLOW_REVIEW_QUICK_ENABLED` 和 `AI_WORKFLOW_REVIEW_DEEP_ENABLED` 后，新执行回到旧的直接调用路径。已产生的 Agent Run、快照、候选和正式数据保留可读；不得删除 V84 表或回填历史候选。进行中的 DEEP 任务应先取消，避免晚到聚合写入。

## 2026-08-31 上线验证记录

- 集成测试环境已顺序覆盖 QUICK、DEEP、失败与取消路径；Agent bootstrap、16 个 Skill 和 6 个工具的注册契约均通过。
- 生产“Agent（新）”已确认 `script-review` 为启用状态，模型为 `deepseek-v4-flash`，最大步骤数为 20，页面可见 16 个 Skill 和 6 个工具。
- 生产 QUICK 第 1 轮完成；DEEP 第 3 轮完成 1/1 单元及聚合，保留 1 份单元候选，生成 1 个正式问题和 2 个正式命中，两个 Agent Run 均成功。
- 回滚演练期间关闭 QUICK/DEEP 开关，新建 QUICK 第 4 轮确认走旧版直连路径，且既有 DEEP 报告、候选、Agent 审计和正式结果保持可读。演练后两个开关已恢复开启。
