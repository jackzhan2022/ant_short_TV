# Redis 查询缓存第一期 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 减少多人查看任务、剧本及公共配置时的重复数据库读取，保持鉴权和数据库写入语义。

**Architecture:** 服务完成鉴权后显式读取 QueryResultCache；Lettuce 单连接 + JSON 加密结果 + 区域版本；事务提交后失效，短 TTL 兜底。

**Tech Stack:** Java 17、Spring Boot 3.3.5、Lettuce（由 Boot 管理版本）、Jackson、Micrometer、JUnit/Mockito。

## 执行顺序

- [x] 基于当前 backend/src 与 pom 的快照建立独立 worktree；ProjectAiConfigServiceTest、ScriptWorkflowReadBoundaryTest 基线通过。
- [x] 写 QueryResultCacheTest：冷热加载次数、隔离、事务绕过、提交/回滚、并发填充、失效竞争、异常与密文篡改。
- [x] 运行 `mvn -q -Dtest=QueryResultCacheTest test`，确认新增缓存接口缺失导致失败。
- [x] 添加 querycache 包：CacheStore、LettuceCacheStore、QueryResultCache、QueryCacheConfiguration、QueryCacheInvalidationAspect；默认禁用、200ms 命令超时、300ms 连接超时、2秒熔断、128KiB 单值限制、TTL 和加密键绑定。
- [x] 修改 ProductionTaskService.summary、MessageCenterService.summary、ProjectAiConfigService.availableModels、ScriptWorkflowService 的导航/版本摘要/analysis。每个接入点位于鉴权之后，只缓存数据，不缓存授权决定。
- [x] 添加消息/模型/剧本/任务写入口的提交后区域失效，验证正常提交与回滚行为。
- [x] 运行查询缓存单测和服务回归；使用缓存开启的服务测试证明 SQL loader 次数下降，权限仍每次检查。
- [x] 使用独立临时 Redis 进行真实驱动认证、TTL、并发连接故障检查及缓存短路恢复测试。保留关闭开关与生产启用/回退说明。
- [x] 复核本次新增差异与测试结果，更新实施记录。

## 文件映射

`backend/src/main/java/com/antshorttv/querycache/` 负责连接、结果缓存、区域版本与提交失效。
`backend/src/test/java/com/antshorttv/querycache/` 负责缓存机制和接入验证。
`backend/pom.xml` 仅新增 lettuce-core。
`backend/src/main/resources/application.yml` 增加 query-cache 默认关闭配置。
四个业务服务只包装已有查询，保持 SQL 和接口响应兼容。
`docs/redis-query-cache-phase-one.md` 记录范围、指标和启用/回退步骤。

## 验证结果

2026-10-21：干净构建及指定 11 个测试类共 47 项测试通过，失败/错误/跳过均为 0。包含隔离 Redis 驱动测试；临时实例和 SSH 隧道已清理。独立复核的首次连接排队与无界 GET 问题已修复并通过回归。测试明细见 `outputs/redis-phase-one-test-results.json`。生产缓存尚未开启。
