## Why

剧本审核当前依赖候选、语义裁决、结构化问题和严格工具 Schema 的多阶段链路，任何中间字段不一致都会让整轮审核失败。主要用户流程是阅读意见后人工改稿，因此新审核应直接生成并保存 Markdown 报告，减少技术性失败并让结果更易阅读和导出。

## What Changes

- 新增 Markdown 审核报告存储和任务响应能力，新任务以模型最终 Markdown 文本作为唯一业务结果。
- QUICK 直接生成一份 Markdown 报告；DEEP 保留长剧本分段与失败重试，但单元结果和最终汇总均改为 Markdown。
- 新审核不再执行候选 Schema 校验、语义裁决、异常门禁、结构化问题聚合、历史问题匹配或终态保存工具调用。
- 工作台按结果格式展示 Markdown 报告或历史结构化问题，Markdown 报告支持复制和下载。
- 保留任务状态、范围、维度、模型调用日志、用量、取消和重试；空响应与供应商明确截断仍视为失败。
- 历史结构化任务、问题、命中、裁决和导出保持只读兼容，不迁移或删除原数据。
- **BREAKING**：新建审核任务不再产生可逐条确认、忽略、批量修复或跨轮匹配的结构化问题。

## Capabilities

### New Capabilities

- `script-review-markdown-report`: 定义 Markdown 报告的生成、原样保存、读取、失败边界和历史格式兼容。

### Modified Capabilities

- `script-review-workflow-agent`: QUICK 和 DEEP 的成功条件从终态工具保存改为非空 Markdown 最终输出，并移除新审核的结构化工具阶段。
- `script-review-deep-fanout`: DEEP 单元保存 Markdown 片段，完整后按顺序汇总 Markdown，并保留单元与聚合重试。
- `script-review-agent-tools`: 新审核不再使用候选、语义裁决和结构化正式结果工具；可信范围读取与权限边界继续保留。
- `script-review-workbench`: 新任务展示、复制和下载 Markdown，历史结构化任务继续使用现有问题工作台。
- `script-review-library`: 项目状态改为兼容 Markdown 报告完成态，不再只依赖未处理结构化问题数量。

## Impact

- 数据库为审核任务增加结果格式和 Markdown 正文字段，并为 DEEP 单元增加 Markdown 结果存储。
- 后端审核适配器、DEEP 协调器、任务响应、导出和特性开关需要调整。
- 前端审核工作台、项目库状态推导、类型定义和测试需要同时支持 Markdown 与历史结构化结果。
- 已有 AI 调用、积分结算、任务生命周期、权限和审计日志继续复用；不新增运行时依赖。
