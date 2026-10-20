# 系统监测与飞书汇总

已部署生产环境，详见 [2026-10-20 发布记录](system-monitoring-release-2026-10-20.md)。

每小时 30 分（Asia/Shanghai，北京时间）推送最近 60 分钟的报错。例如 10:30 汇总 `[09:30,10:30)`，11:30 汇总 `[10:30,11:30)`；恰好发生在边界时刻的事件进入下一批。实际发送从 30 分 10 秒开始，为 AI 任务事务提交留出短暂缓冲，窗口仍以 30 分 00 秒划分。无报错不发消息。

## 启用

在后端启动目录的 `env` 文件或服务器环境变量中设置：

```properties
MONITORING_ENABLED=true
MONITORING_FEISHU_WEBHOOK_URL=机器人Webhook地址
MONITORING_FEISHU_SECRET=
MONITORING_ENVIRONMENT=production
MONITORING_INSTANCE_ID=backend-1
MONITORING_MAX_EVENTS=10000
MONITORING_INCLUDE_AI_TASKS=true
```

Webhook 仅在服务端保存，不写入 Git 或前端构建产物。机器人如启用签名校验，需要同时设置 `MONITORING_FEISHU_SECRET`；如启用关键词，关键词应包含“系统监测”。默认监测关闭；启用但未配置 Webhook 时，后端启动会明确报错。修改配置后重启后端；前端需包含本次构建。

部署时将 Webhook 添加到目标服务器的 `env`，不要提交包含真实地址的配置文件。

## 采集范围

- 前端：登录后的 React 渲染异常、全局 JavaScript 异常、未处理 Promise 拒绝、未收到响应的接口请求失败。监测自身用原生 fetch，不进入请求拦截器；取消的请求、已由后端处理的业务错误不再作为 Promise 异常采集。浏览器每分钟最多发 20 条，文案相同的独立异常分别计数，同一 Error 对象在多处捕获时只上报一次；汇总次数代表实际收到的事件数，超出客户端限频的错误不计入。
- 接口：MVC 中未处理异常、AI 服务超时 / 服务商失败等技术故障。参数错误、未登录、权限、积分不足等预期业务拒绝不推送；沿用原接口响应行为。安全过滤器或网关在 MVC 之前产生的错误不在该采集点中。
- AI 任务：汇总时查询现有 `ai_execution_task` 表中 `completed_at` 落入窗口、当前状态为 `FAILED` / `TIMED_OUT` 的记录。失败尝试仍在等待重试时不统计，已经被用户重试覆盖为其他状态的记录也不统计。数据库时间按现有业务写入方式（后端 JVM 时区的 LocalDateTime）换算。每批最多读取 `MONITORING_MAX_EVENTS + 1` 条，超限提示“至少丢弃”，不执行无界查询。

消息按来源、代码、位置及摘要合并，展示次数、影响用户数和最多 5 个用户 / 团队 / 项目 / 任务 ID 样例。前端用户 ID 来自登录会话；接口及前端事件中的团队 ID 是客户端 header 提供的定位线索，不作为授权依据。AI 任务中的团队、项目和用户来自已有任务记录。

不采集请求体、Cookie、剧本或提示词正文。路径去掉 query/hash，摘要和堆栈截断并脱敏常见 token、密码、手机号。不可将错误摘要脱敏理解为任意敏感业务文本的完整识别。

## 发送与限制

前端和接口事件只存在本进程内存，不新增监测表。默认最多缓存 10000 条，满后丢弃新增事件并在汇总中标注数量；服务重启会丢失未发送记录。发送线程独立于现有业务调度器。

汇总过长时分片，每片包含时间窗口及分片编号。每批最多 20 个分片，超出后省略其余类别明细并说明省略数量，总计数仍包含已采集事件。HTTP 2xx 且飞书业务返回 `code=0`（或 `StatusCode=0`）才视为成功。每片最多尝试三次，成功分片不重发。飞书响应丢失时重试仍可能造成重复消息。三次都失败后本批次不会持久保留，日志仅包含窗口、分片和计数，不输出 Webhook 或完整消息。

多实例部署时，每个进程独立汇总自己的内存事件，应设置不同的 `MONITORING_INSTANCE_ID`。共享同一数据库时，只允许一个实例设置 `MONITORING_INCLUDE_AI_TASKS=true`，其余设为 false，避免重复汇总同一批 AI 任务。

未在整点半执行、停机期间或数据库查询失败时，不补发更早时间窗口。数据库查询失败的当前批次会包含“统计不完整”提示；业务请求和任务执行不受影响。AI 汇总属于任务状态快照；边界前写入完成时间、但在缓冲期结束后才提交的长事务仍可能漏统计，不提供持久化事件审计保证。

## 排查

收不到消息时依次确认：目标服务器监测已开启并已重启、该小时存在采集事件、机器人关键词 / 签名 / IP 白名单满足条件、服务器能访问飞书。搜索后端日志 `Monitoring digest delivery failed` 或 `Monitoring AI failure query failed`。不要把原始 Webhook 写入日志或工单。

## 开发验证（2026-10-20）

- 后端：`mvn -f backend/pom.xml '-Dtest=Monitoring*Test,FeishuWebhookClientTest,ScriptAnalysisErrorContractTest' test`，15 项测试通过。
- 前端：`npm --prefix frontend test -- src/utils/systemMonitoring.test.ts src/requestErrorConfig.test.ts src/app.test.tsx`，32 项测试通过。
- `npm --prefix frontend run lint`（含 TypeScript）及 `npm --prefix frontend run build` 通过。antd lint 退出码为 0，保留项目现有的静态反馈 API 使用提示。
- 指定机器人成功接收一条开发连通验证消息，飞书业务码为 0。
