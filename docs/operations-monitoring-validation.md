# 平台运维监控验证记录

日期：2026-11-05。分支：`codex/platform-operations-monitoring`。

## 功能回归

- 后端最终扩展回归 **137/137**，0 failure/error/skip；包含运维、原监测、同步查询观测、AI 调用日志、执行核心/派发/工作槽位和平台权限。
- 前端最终定向回归 **58/58**，9 个文件；包含 access、刷新竞态、错误指纹、执行详情、权限、规则/设置冲突和阶段/路由作用域。
- 完整 Biome/TypeScript 与 Ant Design 检查退出 0；原代码 14 warnings/4 infos、Ant Design 9 条 requestErrorConfig 使用提示未新增到 operations。最后 route/method 校验改动追加 TypeScript 和相关文件 Biome 检查，最终生产构建退出 0。
- 外部探测标准库测试 **4/4**，覆盖 3 次失败/2 次恢复、重试耗尽、稳定编号、健康响应、HTTP 200 非零业务码及非对象 JSON。没有连接真实 Webhook。
- OpenSpec strict validate 通过；git diff --check 通过。

后端命令：

```powershell
mvn -f backend/pom.xml '-Dtest=Operations*Test,DatabaseQueryObservationTest,JdbcTemplateTimingAspectTest,Monitoring*Test,FeishuWebhookClientTest,AiExecutionPoolsTest,AiCallLogWriterTest,PlatformAuthorizationServiceTest,PlatformPermissionGuardIntegrationTest,AiExecutionCoreServiceTest,AiExecutionWorkerTest,AiExecutionDispatcherTest' test
```

前端命令：

```powershell
npm run lint
npx antd lint ./src
npm test -- src/pages/operations src/access.test.ts
npm run build
```

完整应用集成测试在隔离 H2 中应用迁移至 V175，确认运维模块不引入第二个 Spring DataSource/JdbcTemplate bean、独立池最多两个连接；匿名 health 最小两字段、运维匿名 401、租户 403、平台管理员可查询。没有触碰用户业务数据库。

## 修复后保留的回归

- 源业务 DATETIME 用 LocalDateTime 墙上时间读取再按原 JVM 时区换算；运维 DATETIME 接受 Timestamp 和 LocalDateTime 并统一 UTC。实际依赖 Connector/J **8.3.0** 的 getObject DATETIME 分支默认返回 LocalDateTime，H2 不能代替该兼容检查；有 MySQL 形状的租约 fixture 与上海 JVM/UTC session 源时间回归。
- 同步媒体/压缩包下载、multipart 上传排除交互延迟；JDBC 配置方法不计 SQL，嵌套观测恢复并去重。
- 已知实例失联超过 24 小时仍使全局完整性未知，不依赖其他节点资源样本恢复；水位须在所属桶提交后推进，并保留 10 秒闭合窗口余量。
- 持久摘要遵守 includeAiTasks=false，在容量截断前排除 AI_EXECUTION，统计总数采用相同过滤；默认开启该项仍包含 AI 失败。
- 结算 RESERVED、长期活跃任务、最后 boot、活动告警及关联动作不会因保留期被提前清除。
- 历史 AI 调用可下钻最新保留的执行版本上下文；尝试/调用仍按选定时间，明确 PARTIAL 和每源 100 条上限。

## 持续负载与开销

发生器使用 loopback JDK HTTP server + 实际 OperationsHttpFilter/Telemetry + 隔离 H2 + 固定索引 SQL，每秒发出 100 个真实 HTTP 请求。它不是 Spring 全链路或生产 MySQL 压测；fixture 没有注入资源采样器、调度器和完整性服务，相关机制由上面的功能回归验证。

精确数量的持续运行结果见 [原始 JSON](operations-monitoring-soak.json)：60 秒关闭基线 + 1800 秒开启，共 **186000** 请求（6000 off/180000 on），实际 **1860.010 秒**，错误 **0**。队列峰值 **0/5000**，维度记录峰值 **6/6006**，GC 后堆从 **22,298,400** 增至 **29,403,816** 字节（约 6.78 MiB），有界内存验收通过。

同一次运行的 24 小时/7 天聚合 snapshot P95 分别为 **48.217ms/15.0769ms**，均小于 1 秒；隔离 H2 EXPLAIN 命中 idx_ops_metric_global，计划保留在 JSON。该计划不代表 MySQL 优化器的选择。

该运行的 HTTP P95 为 off **2.093199ms**、on **3.479201ms**，增量 **1.386002ms/66.2145%**，**未通过 5% 门槛**。on 窗口与多轮 Maven/前端构建重叠，off 窗口没有相同负载，不能把差值直接归因于监控；不以小于 20ms 为由忽略百分比门槛。构建全部停止后，使用同一代码/相同负载单独复测开销，结果如下。

独占环境复测 [原始 JSON](operations-monitoring-comparison.json)：60 秒 off + 180 秒 on，**24000** 请求，错误 **0**，实际 **240.003 秒**。HTTP P95 off **2.398799ms**、on **1.372100ms**，增量 **-1.026699ms/-42.8005%**，满足 **≤5% 且 ≤20ms**；最大并发从混合负载运行的 39 降为 **5**。未修改采集代码或验收阈值；该轮未复现开销超标，说明前轮并行构建/时间窗差异影响对比。负增量不作为监控能加速业务的结论。

开销比较与 30 分钟稳定性使用独立运行结果，避免把构建负载差异当作监控开销；180 秒比较轮的 fullDurationPassed=false 是预期的时长标记，30 分钟完整性由 soak JSON 的 true 和 180000 个 on 样本证明。两个运行的查询和内存预算均通过。仍需在目标部署环境按真实业务负载复验。

首轮墙钟发生器曾有 179999 个 on 样本（少 1 个），fullDurationPassed=false；虽然当轮开销/内存预算通过，未据此声称通过完整数量验收。发生器改为精确数量和记录实际时长后才完成上述持续负载。

## 发布门禁与限制

- 尚未做真实 MySQL 迁移/EXPLAIN：本机 Docker daemon 不可用。V175 在业务源表建索引，须在预发布验证索引耗时、锁等待、查询计划和真实数据量。
- 尚未生产部署、真实飞书试发或部署独立主机探测；externalProbeConfigured 仍为人工确认字段。
- 源对账有界、采集异步、历史版本可能丢失；缺数据不当作成功或恢复。通知远端响应丢失可能重复，使用稳定编号识别。
- 已知实例采用保守登记，无自动退役/强制恢复；有计划退役按 runbook 维护实例及规则作用域。
- 性能验收只对注明的 fixture 有效。生产发布仍须同样检查真实流量、连接池和数据规模。

使用、发布、告警处置及回退见 [运维手册](operations-monitoring-runbook.md)。
