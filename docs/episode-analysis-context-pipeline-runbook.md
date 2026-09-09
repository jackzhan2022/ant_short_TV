# 分集解析共享上下文流水线运行手册

## 开关与启用顺序

新流水线仅影响开关开启后创建的任务，历史任务继续按 `LEGACY_V1` 执行。

1. 先开启 `AI_WORKFLOW_EPISODE_CONTEXT_PIPELINE_ENABLED=true`，验证新任务记录为 `EPISODE_CONTEXT_V2`。
2. 确认逐集概要和资产识别稳定后，再开启 `AI_WORKFLOW_AUTO_STORYBOARD_ENABLED=true`。
3. 自动分镜沿用项目文本模型、`STORYBOARD:AI_BREAKDOWN` 权限和积分预扣；积分不足只阻塞分镜，不回滚资产识别。

## 预算与并发

- 概要：`AI_WORKFLOW_EPISODE_SUMMARY_RUN_TIMEOUT_SECONDS` / `AI_WORKFLOW_EPISODE_SUMMARY_REQUEST_TIMEOUT_SECONDS`
- 识别：`AI_WORKFLOW_ASSET_RECOGNITION_RUN_TIMEOUT_SECONDS` / `AI_WORKFLOW_ASSET_RECOGNITION_REQUEST_TIMEOUT_SECONDS`
- 分镜：`AI_WORKFLOW_STORYBOARD_RUN_TIMEOUT_SECONDS` / `AI_WORKFLOW_STORYBOARD_REQUEST_TIMEOUT_SECONDS`
- 单模型跨实例并发：`AI_EXECUTION_MAX_CONCURRENT_PER_MODEL`，默认 4；数据库配额行保证多实例一致。
- 单租户并发：`AI_EXECUTION_MAX_CONCURRENT_PER_TENANT`。领取顺序为优先级、创建时间、ID；排队发生在运行预算开始前。

不要通过统一抬高 300 秒超时处理慢请求。先查看逐集 Run 的模型调用时长、请求轮数、输入 tokens 和缓存遥测，再单独调整对应阶段。

## 状态与恢复

- 概要与识别是独立分支；其中一支失败不会阻断另一支。
- 资产 coverage 成功后，同一事务写入自动分镜事件。事件处于 `DISPATCHING` 超过 120 秒会恢复为 `RETRYABLE`。
- `BLOCKED_FUNDS`、`BLOCKED_AUTH`、`PROTECTED`、`STALE` 均为明确终态，不做无限重试。
- 自动与手动分镜生成共用正文版本准入记录；同一正文正在生成时返回既有执行，避免重复预扣。
- 服务重启后由执行租约恢复器、fanout 快照和自动分镜事件继续处理，不依赖内存 Future。

## 缓存与耗时核对

缓存键包含租户、模型和冻结上下文哈希。只比较相同模型、相同正文指纹和相同协议版本的调用。`cached_input_tokens` 缺失必须显示为“未知”，不能记为 0；供应商返回明确 0 时才计算为零命中。首字节时间在供应商未提供时保持空值。

发布验证至少记录请求轮数、输入 tokens、已知/未知缓存调用数、已知命中率、费用、成功率和模型耗时 P50/P95。禁止把测试环境 mock 数据当作供应商缓存结论。

## 历史错误分集与回滚

历史上被合并为分组的剧本不会自动重写。由用户显式点击“重新 AI 分析”，新任务才执行单集标题粒度校验；旧集和人工成果按稳定集 ID 对齐与退休规则保留。

回滚步骤：

1. 关闭 `AI_WORKFLOW_AUTO_STORYBOARD_ENABLED`，停止创建和消费新的自动事件。
2. 关闭 `AI_WORKFLOW_EPISODE_CONTEXT_PIPELINE_ENABLED`，新任务恢复 `LEGACY_V1`。
3. 等待已有 `EPISODE_CONTEXT_V2` 执行完成或通过现有取消入口终止；不要在新任务仍运行时回滚程序。
4. 保留新增快照、事件、配额和准入表用于审计，不删除已生成分镜或人工编辑。
