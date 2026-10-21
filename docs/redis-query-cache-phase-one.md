# Redis 查询缓存第一期

日期：2026-10-21。本次补充按用户/项目/租户失效，并延长稳定展示数据的有效期。默认关闭，线上 Redis 的 2 GiB 配额维持现有配置。

## 已接入的查询

| 入口 | 缓存内容 | TTL | 隔离维度 |
| --- | --- | --- | --- |
| ProductionTaskService.summary | 任务状态分组统计及总数 | 2 秒 | 租户、用户、已校验的过滤条件 |
| ScriptWorkflowService.scriptPageWorkspace | 版本摘要、剧集导航 | 60 秒 | 租户、项目、剧本及查询类型 |
| ScriptWorkflowService.currentAnalysis | 当前分析任务进度聚合 | 2 秒 | 租户、项目 |
| MessageCenterService.summary | 各分类未读数 | 5 秒 | 当前用户 |
| ProjectAiConfigService.availableModels | 已有公共模型选项响应 | 120 秒 | 全局公共选项 |

每次请求仍执行原来的身份、租户、项目访问和筛选权限检查。消息 canSend 每次重算。任务列表、剧本正文、账户/余额/扣费、实际模型调用路由、任务领取和执行状态写入继续使用原逻辑。工作台其他查询没有因此自动全部缓存。

## 减少哪些 MySQL 读取

同一键在有效期内再次访问，可以省去上述查询的 loader；鉴权 SQL 和未接入查询仍会执行。模型选项可以在已通过鉴权的不同项目请求间复用，剧本分析可以在同项目的已授权用户间复用。任务统计包含用户维度，不能理解为所有用户共用一份统计。

服务测试对两次相同请求验证：

- 消息分类统计：实际 H2 聚合查询由两次变为一次；权限接口仍调用两次。
- 任务统计：聚合查询调用由两次变为一次；团队访问仍检查，权限撤销后拒绝访问。
- 模型选项：四种模型类型的 selectList 合计调用四次，而非两次请求的八次；项目访问仍检查两次。
- 剧本页：版本查询和导航查询各调用一次，缓存读回的响应 JSON 与首次响应一致；访问权限撤销后拒绝访问。

任务/分析页面约5秒轮询，2秒短缓存主要用于并发时间窗口内复用，不能据此推断单用户跨轮询必然命中。本轮重点让稳定数据缓存保留更久，并减少不相关写入对命中的影响。

这是针对重复查询的测试结果，不是生产 MySQL 总负载或接口延迟的降幅。预期可感知改善是多人查看进度、反复打开任务统计/消息角标/剧本页时查询更稳定；AI 供应商生成耗时及任务执行并发没有在本期调整。实际收益受轮询间隔、重复访问、权限查询耗时及主动失效频率影响。

## 一致性和故障处理

- 写入口提交后更换对应作用域的版本，回滚不失效。同一事务内相同 region＋scope 的通知合并为一次。
- 消息已读/删除仅使当前用户失效；定向发送和系统消息仅使实际收件人失效。全员广播更新公共广播代次，用户未读缓存同时依赖自己的代次和广播代次，因此全员通知会刷新所有用户。这是有意保留的全局行为。
- SCRIPT 和 ANALYSIS 按“租户＋项目”失效。剧本版本的 AI 生成/改写、项目创建、视频导入、手动保存/应用都在实际写入处通知；剧集重排、概要 repository.upsert 和 AI 正文保存后的内容指纹更新也通知对应项目。
- TASKS 按租户失效，但结果键仍包含用户和权限过滤条件，不会跨用户复用不同权限的统计。执行状态切面在缓存开启且状态变更成功后，查询一次执行记录取得所属租户/项目；心跳、领取失败不额外查询或失效。超时恢复直接使用已查询的任务，仅通知更新成功的项。
- MODELS 仍是平台公共数据，平台模型/供应商配置事务更新后全局失效；实际调用路由不使用此缓存。
- 失效前开始的加载只能填充旧版本键；失效后读取使用新版本。外部 SQL 或未覆盖的后台写入依赖 TTL 更新展示，不能用于强一致决策。
- 数据库事务中的读取绕过缓存，不发布事务内快照或未提交值。
- Redis 命令超时 200ms，TCP 连接超时 300ms；首次连接锁最多等待 50ms，失败后冷却 2 秒。读取故障回源数据库并短路 2 秒。批量失效遇到首次 Redis 故障立即停止，冷却期间不继续逐用户重试；本次未成功通知的缓存依赖原 TTL 到期，Redis 故障时不承诺提交后立即刷新。
- 单机按键合并填充，等待超过 50ms 则直接回源；跨进程没有分布式填充锁，因此不保证整个集群只执行一次 SQL。
- 业务 loader 异常原样抛出，单次请求不会因缓存写入失败而重复执行 loader。
- Redis 使用一个复用连接，断线命令拒绝排队，命令队列上限 64。

## 数据保护与容量

命名空间 `antv:query:v2:`，与旧版全局缓存键隔离。作用域和身份参数以 SHA-256 摘要进入键。JSON 使用现有 AiSecretCodec 的 AES-GCM 加密，使用 `antv-query-cache-v1:` 作为独立密钥域。密文内绑定完整键及绝对到期时间，拒绝跨键重放、篡改和过期数据；反序列化类型由服务端指定。

JSON 上限 128 KiB、密文上限 192 KiB。Redis GETRANGE 单次最多取回 192 KiB + 1 字节；区域版本最多取回 65 字节。超限或非法数据回源。共享 Redis 的其他全键账号仍可能删除键，因此缓存只承载可重建数据。

Redis 配额仍为之前设置的 2 GiB、noeviction。缓存内存不足时 SET 失败，查询返回数据库结果；本期不修改该共享实例的淘汰或持久化策略。

## 配置与生产启用

应用默认 `query-cache.enabled=false`，不会创建 Redis 客户端资源。启用需要：

- `QUERY_CACHE_ENABLED=true`。
- `SPRING_DATA_REDIS_HOST/PORT/USERNAME/PASSWORD/DATABASE`，来自现有私有文件 `/opt/antv/shared/redis.env`。
- 有效的 `ai.secret-key`（环境变量 `AI_SECRET_KEY`），或单独提供 `query-cache.secret-key`。所有实例须使用一致的密钥；未配置密钥则启动失败。

本客户端的短超时由 LettuceCacheStore 固定设置，不使用 redis.env 中的 2s/3s 通用超时变量。密钥/口令不得进入仓库、日志或发布包。

部署前先使用与线上发布基线一致且已验证的后端构建。本工作树包含开发工作区已有的其他未提交功能，不能直接把整个工作树作为本期发布内容。首次开发阶段未替换生产 JAR、重启生产服务或开启线上缓存；本次正式发布状态见 `redis-query-cache-release-2026-10-21.md`。

合适的发布包部署后，可在 antv.service 的专用 drop-in 中加入：

```ini
[Service]
EnvironmentFile=/opt/antv/shared/redis.env
Environment=QUERY_CACHE_ENABLED=true
```

由既有发布流程执行 daemon-reload 和服务重启。先验证健康检查、登录/项目访问、两次相同展示请求、写入后刷新和后台任务正常运行，再观察缓存指标与数据库负载。不要对共享 Redis 执行 FLUSHDB/FLUSHALL。

回退：将该 drop-in 中开关设为 `QUERY_CACHE_ENABLED=false` 并按发布流程重启，恢复全部查询直读数据库；缓存数据自行过期，不需要修改业务数据。

## 观测与验收

Micrometer 计数器 `antv.query.cache`，标签仅为有限的 region 和 outcome；outcome 包含 hit、miss、fill、bypass、contention、invalid、oversize、error、invalidation、invalidation_bypass。未新开公网监控端点，也未增加前端监控面板；指标需要现有采集通道或后续受控监控接入读取。

生产验收对比同等并发、同等请求频率下的接口 p50/p95、缓存命中率、MySQL 查询速率/活跃连接/慢查询、Redis 内存/拒绝写入。先取得基线，再比较开关启用后数据；不得把测试 SQL 次数直接写成生产性能提升百分比。

测试覆盖缓存冷热加载、隔离、默认关闭、密钥必填、事务绕过、真实事务提交/回滚、失效竞争、篡改/重放/到期/超限、空值、并发填充、缓存写失败和短路恢复，以及消息/任务/模型/剧本接入。真实 Redis 驱动检查使用数据库主机上的临时回环实例及 SSH 隧道，独立端口、64 MiB、禁用持久化，测试后清理；不修改生产 Redis 业务数据。

### 首轮执行结果（细化前）

干净构建后指定 11 个测试类，共 **47 项通过，0 失败、0 错误、0 跳过**。这不是后端全量测试集。独立代码复核未发现遗留阻断问题。

```powershell
mvn -q '-Dtest=QueryResultCacheTest,QueryCacheServiceTest,QueryCacheTransactionTest,QueryCacheConfigurationTest,LettuceCacheStoreIntegrationTest,ScriptQueryCacheTest,ProjectAiConfigServiceTest,ScriptWorkflowReadBoundaryTest,MessageCenterControllerTest,ProductionTaskControllerTest,ProjectReadPerformanceIntegrationTest' clean test
```

执行真实 Redis 用例时，测试进程需设置 `QUERY_CACHE_TEST_PORT` 和 `QUERY_CACHE_TEST_PASSWORD`，连接 localhost 隔离实例或隧道，ACL 用户为 antv_app，仅允许 antv:*。未设置端口时这两个用例会跳过，本次通过隔离实例实际执行。密码只注入测试子进程，不写入日志。

明细：`outputs/redis-phase-one-test-results.json`。本期增量补丁：`outputs/redis-query-cache-phase-one.patch`。首次开发验证时未提交 Git、发布生产包或测量生产延迟；后续发布记录见 `redis-query-cache-release-2026-10-21.md`。

### 本轮新增验收范围

确认用户 A 的消息操作不导致 B 的未读聚合重查；项目 A 的导航或概要更新不影响项目 B（包括另一租户的相同项目号）；执行任务变化只影响对应租户统计及对应项目分析；广播可以使所有用户失效。验证事务回滚、同事务重复通知合并、500 收件人通知遇到 Redis 超时只尝试首个失败命令，以及 AI 保存正文后内容指纹的提交后刷新。

稳定缓存的 TTL 延长并不表示每次变更都要等 60/120 秒：正常写入成功后通过作用域版本让下一次读取重新加载。外部 SQL 或 Redis 失效通知失败仍依赖 TTL；前端下一次请求的时机由页面决定。

### 作用域细化后的执行结果

2026-10-21：缓存专项11个测试类共 **41项全部通过**，包括真实隔离Redis驱动用例；完整指定回归20个类共143项，141通过、2错误、0跳过。不是后端全量测试。

两项错误均为 `ScriptWorkflowControllerTest` 中测试辅助方法重复创建内置Agent（`Agent code 已存在`）：

- `automaticStoryboardResumesFundsAndRecoversSubmissionWithoutDoubleReservation`
- `createsAndReanalyzesInitialScriptTasks`

在原项目、本轮修改前源码上执行 `clean test` 单独复跑，得到相同方法、相同异常，确认是既有测试数据冲突；本轮没有修改Agent业务或绕过这些用例。原项目首次非clean复跑还遇到旧构建产物中的重复迁移文件，clean后该干扰消除。不能把此次扩展回归称为全部通过。

独立复核发现的批量失效超时放大及AI正文指纹失效遗漏均已修复并验证，后续复核没有遗留阻断发现。临时Redis实例及SSH隧道已清理。生产缓存未开启。

本轮明细：`outputs/redis-scoped-cache-test-results.json`；增量补丁：`outputs/redis-scoped-cache.patch`；合回校验：`outputs/redis-scoped-cache-integration-receipt.json`。
