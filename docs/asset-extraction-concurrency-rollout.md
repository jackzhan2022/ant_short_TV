# 资产提取并发发布与验收

对应 OpenSpec：`stabilize-asset-extraction-concurrency`。本变更使用现有执行调度器，不新增消息队列。

## 发布准备

1. 从隔离分支提取本变更，核对审计文件中列出的在途依赖。不要把工作区里其他未完成的审核或任务中心改动整体发布。
2. 暂停两个资产提取入口接单，排空旧版本的 `SCRIPT_ANALYSIS_TASK` 及 `SCOPED_ASSET_REEXTRACTION` 执行。若需取消，通过正式任务取消接口操作，等待旧 worker 停止；不能只修改业务表的状态。
3. 备份数据库和配置的 `workflow-agent.skill-root`。执行 `scripts/sql/asset-extraction-duplicate-preflight.sql`；发现非空 active identity 重名时停止迁移，人工确认，不自动合并或删除。此前零冲突的审计结果不能代替发布时检查。
4. 迁移 V113 创建协调表与三类 active identity 唯一索引；在既有 V114/V115 旧链路下线迁移之后，V116 增加既有 Agent 的两个只读工具、提升 revision 和步数，V117 为重提取快照增加冻结计划。MySQL DDL 可能部分成功，失败时逐项核对列、索引及 Flyway history，不盲目重放整份 DDL。
5. 同步后端、前端和 bundled Skills。启动时 `AssetCatalogSkillUpgrade` 给已持久化 Skill 追加带 `asset-catalog-protocol:v1` 标记的协议，保留用户内容，以 revision 校验并原子替换；重复启动不重复追加。若 Skill 不存在，先按原有安装流程安装 bundled Skill。确认 Skill 文件可写，所有节点共用同版本协议。
6. 确认 Agent allowlist 包含 `read_current_episode`、`search_script_assets`、`read_asset_details`、`save_episode_assets`，max_steps 至少 12；共享上下文协议为 `episode-shared-tools-v2`。恢复接单前确认没有旧 worker。

## 隔离环境验收

使用全新、仅供测试的 MySQL schema，不得给测试命令配置线上 URL。示例（PowerShell，URL/用户名由测试环境提供）：

```powershell
mvn -f backend/pom.xml "-Dtest=AssetExtractionCoordinationTest,ScreenplayToolDataServiceTest,AssetCatalogServiceTest,AssetCatalogSkillUpgradeTest" "-Dspring.datasource.url=jdbc:mysql://127.0.0.1:13316/asset_concurrency_test?allowPublicKeyRetrieval=true" "-Dspring.datasource.username=root" "-Dspring.datasource.password=" "-Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver" test
```

该测试仅适合本地隔离实例。模型由测试替身提供，不调用付费模型。断言涵盖：

- 不同客户端 key 同时提交，只创建一个 operation、execution、reservation 和 RESERVE 账目；不同用户、范围或策略收到冲突。
- 剧本页与资产页互斥，不同剧本可独立申请；多次等待不消耗失败重试预算。
- 取消、过期租约及新 attempt 接管后，旧 worker 不能保存或释放新 owner。
- 正式保存已提交、单元尚未成功就崩溃时，恢复复用同一提交证据，不重复调用 Agent；收口重放不重复清理。
- 源版本或正文变化禁止保存/收口；提交证据被后续任务替换时，旧任务必须重新提交，不能直接覆盖新结果。
- 手动资产与范围外资产保留；未引用 AI 资产只在完整有效证据下退役；软删除历史不阻碍新建同名 active identity。
- 215 个道具、51 个形态可分页，别名可查到初始候选外资产，游标不能跨查询/运行；超大单条内容明确报错。

其他后端回归使用项目默认 H2 测试配置。历史执行核心测试假设独立内存数据库与亚秒时间精度，不应直接在已累积多轮数据的 MySQL schema 中运行其全局数量断言；真正的并发验收以专用 MySQL 测试为准。

前端执行 `npm run test -- src/pages/projects/production-workbench/settings.test.tsx src/pages/projects/production-workbench/useAssetExtractionTracking.test.tsx src/services/ai-execution/task.test.ts`、`npm run lint` 和 `npx antd lint ./src`。

## 本次验证结果（2026-09-12）

隔离分支 `codex/stabilize-asset-extraction-concurrency` 完成以下检查：

- 后端相关 18 个测试类最新报告合计 147 个用例，失败和错误均为 0。首次回归发现的共享保存主键差异与工具契约断言已修正，并完成相关类重跑；该统计是各类最终报告汇总，不是全项目测试。
- 独立 MySQL 8.4.9 上协调集成测试 13 个、正式工具保存测试 38 个通过，后者在最终主键兼容修正后再次通过。涵盖真实事务竞争、别名复用、歧义拒绝、绑定幂等及 FILL_EMPTY / REGENERATE_ALL；测试未调用付费模型。
- 前端 settings、任务跟踪 hook、执行轮询共 32 个测试通过。`npm run lint`（含 TypeScript）退出码 0，保留 6 条已有 Biome 警告；`npx antd lint ./src` 退出码 0，保留 13 条已有警告。
- 本次涉及路径的 `git diff --check` 通过。独立代码复核发现的冻结计划、旧 claim 收口及提交证据被替换问题已修正并复核。
- 本轮未部署线上，项目 33 的真实模型、费用与浏览器端验收留在下述发布步骤执行。隔离工作区仍保留其他在途变更，发布时需按依赖核对，不能整体提交全部工作区文件。

## 项目 33 线上验收（发布阶段执行，会调用模型）

1. 记录当前三类 active 资产数量、形态数量及非空提示词数量，保留抽样人工修改的资产/提示词。历史参考值为角色 110、场景 152、道具 215，以验收当天数据为准。
2. 选择 PROP + FILL_EMPTY，按页面 preflight 确认。记录返回 execution ID。快速重复点击或另标签页提交相同条件，应指向同一任务与同一预占；换 ALL 或 REGENERATE_ALL 应显示冲突并跟踪已有任务。
3. 刷新资产页，确认恢复原 execution 的轮询，不重新 POST 付费任务。失败/取消/成功应结束 loading；网络失败保留任务引用，查看任务按钮仅恢复读取。
4. 在执行记录中逐集核对正式工具调用和 `scoped_asset_reextraction_unit` 进度。目录响应不再因 215 道具总数失败；候选缺失时有检索/详情调用，不能把漏召回作为新建证据。
5. 全部单元 SUCCEEDED 且 coverage 与 child_run_id/源指纹对应后才收口。确认角色、场景和手动资产未变，已有非空提示词未被 FILL_EMPTY 替换，同一规范名未新增重复 active 资产。
6. 另开经过确认的测试运行验证 CHARACTER、SCENE、ALL 和 REGENERATE_ALL；费用归属保持提交用户。跨入口验收应优先在测试项目进行，避免为了制造竞争重复生成项目 33。

排障只读 SQL（按真实 tenant/project/script/execution/operation ID 绑定参数）：

```sql
select o.*, e.status, e.claim_expires_at
from script_asset_extraction_owner o left join ai_execution_task e on e.id=o.execution_id
where o.tenant_id=? and o.project_id=? and o.script_id=?;
select id,status,error_code,error_message,execution_version,point_settlement_status
from ai_execution_task where id=? and tenant_id=?;
select u.episode_id,u.status,u.child_run_id,u.content_fingerprint,a.generated_by_run_id,a.content_fingerprint
from scoped_asset_reextraction_snapshot s join scoped_asset_reextraction_unit u on u.snapshot_id=s.id
left join script_episode_asset_analysis a on a.episode_id=u.episode_id and a.tenant_id=s.tenant_id
where s.operation_id=? and s.tenant_id=? order by u.id;
```

终态任务仍出现在 owner 表是允许的；下一次申请在行锁内确认终态后替换，不需要手动删除协调行。`RESOURCE_WAIT` 表示资源等待，不能按模型失败处理。源快照变化错误需重新 preflight 后创建新操作。

## 回滚

先停接单，排空或正式取消新执行并等待 worker 停止，再回滚兼容的后端、前端、Agent allowlist 与备份 Skill。保留 V113、V116–V117 的新增表、列、索引和审计记录，不撤销已产生的业务数据或积分账目。回滚旧代码也必须保证只有一个版本执行提取；旧链路没有本次的并发保障，不可混跑。

## 已知边界

不能保证供应商侧 exactly-once：模型已响应但调用结果/正式保存尚未持久化时崩溃，恢复仍可能再次调用。目录候选不是完整事实库，保存端保留精确名称/明确别名匹配与歧义拒绝。元数据检索在服务端进行完整身份匹配，再按稳定 ID 输出有界页面；32 KiB 是给 Agent 的响应预算，不是数据库总目录大小限制。
