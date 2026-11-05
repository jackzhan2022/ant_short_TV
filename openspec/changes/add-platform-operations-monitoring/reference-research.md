# sub2api 运维模块对照研究

研究日期：2026-11-04，Asia/Shanghai。参考仓库：[Wei-Shaw/sub2api](https://github.com/Wei-Shaw/sub2api)，固定读取提交 `6f92090d7f540efd88cd4b81e651120cb36e3ff5`，避免后续上游变更影响本次结论。研究只读取公开源码；下载副本保存在被 Git 忽略的 `.temp/sub2api-monitoring-reference/`。

## 已核对的实现

| 上游源码 | 确认的做法 | 本系统适配 |
| --- | --- | --- |
| [OpsDashboard.vue](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/frontend/src/views/admin/ops/OpsDashboard.vue) | 时间和平台/组筛选、统一刷新节奏、核心快照、延迟/吞吐/错误趋势、日志与详情下钻 | 用平台/团队/项目/模型维度；拆为 React 页面与业务组件，详情按需加载 |
| [ops_dashboard_models.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_dashboard_models.go) | 业务拒绝与服务错误分开，QPS/TPS、延迟分位数、系统资源、后台心跳 | HTTP 成功率、AI 调用成功率、业务任务成功率分别计算；文本 tokens 与图片/视频任务量分开 |
| [ops_metrics_collector.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_metrics_collector.go) | 系统资源与数据库/Redis状态采集、并发队列、采集任务自身心跳 | Micrometer/MXBean、Hikari、查询缓存和本系统执行槽位/租约；区分 JVM、容器与主机 |
| [ops_alert_models.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_alert_models.go) | 规则阈值、窗口、持续时间、冷却、事件、静默 | 复用飞书通道；加入样本量门槛、确认状态、通知历史和恢复证据 |
| [ops_alert_evaluator_service.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_alert_evaluator_service.go) | 稳定窗口评估、持续超限、冷却、通知限流、分布式锁与自身心跳 | 使用 MySQL 短租约与事务唯一键，持久化规则状态和通知 outbox，适配现有 MySQL 架构 |
| [ops_aggregation_service.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_aggregation_service.go) | 分钟/小时聚合、稳定窗口、聚合任务协调 | 从有界计数和轻量调用投影生成分钟桶，再合并小时桶；不在刷新时扫描历史正文 |
| [ops_cleanup_service.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_cleanup_service.go) | 保留期、分批清理、多实例协调、清理心跳 | 仅清理本模块的运维数据；保留业务、调用和积分原始记录 |
| [ops_health_score.go](https://github.com/Wei-Shaw/sub2api/blob/6f92090d7f540efd88cd4b81e651120cb36e3ff5/backend/internal/service/ops_health_score.go) | 后端计算业务与基础设施加权健康分，包含首 token 延迟 | 首期采用有证据的正常/告警/异常/数据不足状态；本系统尚无完整 TTFT 采集，不虚构该指标 |

上游的账号池切换、订阅账号限额、邮件通道和 Go/PostgreSQL 技术细节不直接映射成本系统需求。参考的是可观察性和故障处置方式，未将下载的上游源码作为本项目实现提交。

## 当前系统已有基础

- `monitoring/` 已采集前端、接口异常，并按北京时间每小时 30 分汇总；`MonitoringBuffer` 只保存在内存，发送失败后无持久化补投。
- `observability/` 已统计部分生产工作区接口的 HTTP 耗时、SQL 次数和累计耗时，目前主要落日志。
- `ai_call_log` 已有服务商、模型、场景、耗时、tokens、trace、execution、attempt 与技术/业务结果。
- `ai_execution_task`、`ai_execution_attempt` 已有任务状态、版本、阶段、等待时间、租约、重试和结算状态。
- 已引入 Actuator/Micrometer、平台权限、操作审计、MySQL 和可选 Redis 查询缓存，无需另建用户/权限体系。

## 历史证据的边界

- [调度历史记录](../../../docs/tencent-cos-media-storage-handoff.md) 记载订阅处理占用公共调度线程，以及线上设置 `spring.task.scheduling.pool.size=4` 的缓解措施；不能把仓库未配置池大小直接等同于线上仍为单线程。
- [自由创作性能发布](../../../docs/free-creation-performance-release-2026-11-02.md) 记载列表请求由约 76.3 秒改善为约 0.3～1.2 秒；这是历史测量，非本次压测。
- [分镜慢接口排查](../../../docs/storyboard-page-performance-2026-10-21.md) 记载大字段回传与逐项查询问题；监控需同时记录 HTTP 延迟、SQL 次数/时间，不能仅凭慢请求自动判定缺索引。
- [项目75恢复记录](../../../docs/script-analysis-project75-recovery-2026-11-04.md) 表明该次失败来自资产匹配歧义；不应混为服务商故障或调度拥塞。

本次研究未访问真实线上日志、数据库或监控接口，没有运行上游系统，也没有验证其所有页面或所有功能。后续验收需以本项目测试、故障注入和受控负载测量为准。
