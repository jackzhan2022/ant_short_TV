## 基线与复用交点

- 线上长剧本基线：原文约 130342 字符，存在第 1–59 集显式标题，旧拆分产出为 8 个剧情分组（1–19、20–24、25–28、29–34、35–40、41–45、46–51、52–59）。
- 概要 Agent 旧基线：首次“读取本集”请求耗时 65–299 秒；部分第二请求 117–223 秒；全局 Run 预算 300 秒，已出现首请求和第二请求分别耗尽预算。
- 分镜复用 `StoryboardBatchService` 的批次幂等键、`StoryboardToolDataService` 的确定性来源/覆盖校验，以及 V100 批次执行状态。
- 租约恢复复用现有 `AiExecution` 领取、attempt/version 和结算链路；识别 finalizer 仍使用 `AssetRecognitionFinalizer`，不由自动分镜改写识别结果。
- 缓存复用 `ReviewPromptCacheContextFactory` 的确定性序列化、内容哈希缓存键与 V90 `ai_call_log` 字段；不改动审核流程。
- 未归档交点：`add-storyboard-workflow-agent`、`optimize-storyboard-deterministic-validation`、`stabilize-ai-execution-lease-recovery`、`optimize-script-review-on-demand-loading`。本变更仅调用其稳定入口，新状态和事件使用独立增量迁移。

## 回归夹具

- `episode-59-with-8-groups.txt`：59 集/8 分组结构的匿名摘要。
- `mixed-headings.txt`：目录、中文数字、阿拉伯数字、分组和引用混合。
- `titleless-script.txt`：无显式标题回退。
