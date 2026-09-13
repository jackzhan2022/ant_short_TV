# 历史数据清理清单

实现：V114 Java 事务数据清理 + V115 SQL 结构清理。原历史迁移保持不变；V113 预留外部变更。本次仅在隔离测试库验证，尚未对实际部署库执行。上线前停止旧版本 worker，针对明确的目标连接备份并采集下列计数。

## 精确范围

| 对象 | 退役选择 | 保留规则 |
| --- | --- | --- |
| 元素提取 execution | scene 精确属于 script_element_extract、character_extract、scene_extract、prop_extract；并集包含 ELEMENT_EXTRACT operation 的同租户同 business_type/business_id execution | scoped_asset_reextraction 及其它 scene 不受影响 |
| 分析任务 | pipeline_version='LEGACY_V1'，且没有关联 stage 的 Agent run、同租户同项目同 task 的正式分析 Agent run、任何 task fanout snapshot | EPISODE_CONTEXT_V2 及有上述 Agent/fanout 凭证的旧标记任务完整保留 |
| 审核任务 | coalesce(result_format,'')<>'MARKDOWN' | MARKDOWN task、fanout、片段和导出保留 |
| definition 权限 | PLATFORM_AI_AGENT_VIEW、PLATFORM_AI_AGENT_EDIT、PLATFORM_AI_SKILL_EDIT | 当前 WorkflowAgent/Skill 权限及 ELEMENT:AI_EXTRACT 保留 |
| 审核旧绑定 | 所有 Agent 中精确退役的保存/候选读取工具和 script-review-semantic-quality Skill 绑定 | 全部 Agent 主行、管理员 prompt/model/revision 及当前读取工具绑定不变 |

分析任务使用 Agent 标识 short-drama-global-understanding、short-drama-episode-splitting、short-drama-episode-summary、short-drama-asset-recognition。仅凭 LEGACY_V1 不足以删除当前工作。所有共享 execution 关联同时限定 tenant 和 domain，不按 TEXT capability 或 script_analysis/script_review scene 整批取消。

## V114：单一 DML 事务

1. 在删除标识列之前收集旧 domain ID 及其精确 execution 集合，包括同 domain 的再生成执行。
2. 锁定 execution/reservation/account，仅将 PENDING/RUNNING execution 和 STARTED attempt 设为 CANCELED。保留已终结的业务状态及 provider/contact/transport 证据；清除旧执行的 retryable/调度时间和运行 claim。
3. 仅处理 RESERVED、SETTLEMENT_REVIEW_REQUIRED 预占，remaining = reserved_points - settled_points - released_points。负余量或账户不足即抛错并回滚整组取消、账户、流水及清理；不取 max(0)，不造账户、不造模型。
4. 释放动作与 PointAccountingService 一致：balance += remaining、reserved_balance -= remaining、total_released += remaining、version += 1；reservation 累加 released_points、状态改 RELEASED。不改 settled_points/refunded_points，不退款已消费积分。
5. 每笔正余量追加 point_ledger RELEASE，幂等键 migration:V114:reservation:<id>:release，保存 tenant/user/execution/version/business/reservation/policy、释放额和释放后余额快照。重复 cleanup 不追加流水；账本已有键但 reservation 仍待释放视作不一致并中止。
6. 同步对应 execution_version 的预占汇总。共享 execution、attempt、reservation、point_ledger、调用/成本/用量、价格与策略历史全部保留。
7. 删除旧审核快照之前，按旧 task + tenant + project 精确定位 FORMAL/script-review run，以及 task、snapshot、unit、result 显式关联的 run；只将未终结 run/step 设为 CANCELED 并补 finished_at，保留终结状态、prompt/input/output/调用证据与当前 Markdown run。然后删除旧 review_task 的 export 记录及 fanout 子链、旧 task；旧 review_project.last_task_id 置空。review_project、review_script_version 与导出的物理文件不删除。旧分析 config/result/stage/task 按子级顺序删除；正式资产、概要和 WorkflowAgent run 不删除。ELEMENT_EXTRACT operation 保留为已取消的历史。
8. 当前 script_analysis_config_snapshot 保留 id/task_id/created_at，将 JSON 收敛为有效的 modelId；缺少有效 modelId 的当前快照明确报错并回滚，不回退默认模型。旧任务快照随旧任务清理。
9. 精确删除旧权限绑定及权限、旧审核工具/Skill 绑定。管理员 Agent 主行和模型配置不重写。

## V115：结构清理

按子表优先顺序删除：

- review_semantic_decision、review_candidate_audit、review_pipeline_stage；
- review_issue_event、review_issue_hit、review_batch_repair、review_issue；
- script_asset_promotion_decision、script_asset_candidate_alias、script_asset_candidate、script_asset_normalization_run；
- ai_agent_skill、ai_agent_definition、ai_skill_definition。

删除 idx_script_analysis_task_pipeline 后删除 pipeline_version；删除 script_episode.summary 镜像列，不新增兼容回填，已有正式 script_episode_summary 原样保留。删除 config snapshot 的 agent_code、agent_version_no、skill_versions_json、model_parameter_profile_id、model_parameter_version_no，仅保留模型 JSON。ai_model_parameter_profile 表保留。

删除 review_task.result_format/result_json/global_index_json，保留 report_markdown；删除 review_unit_result.coverage_json/candidates_json，保留 Markdown 片段表。review_fanout_unit.candidate_saved 重命名 report_saved，stage_type 默认改 DIMENSION_MARKDOWN，与新仓储一致。

V115 为 MySQL DDL，不能依赖事务回滚；失败时根据 Flyway 状态和实际 schema 恢复或从部署前备份重演，不能把 V114 财务释放再次人工执行。

## 部署前只读采集

```sql
select database() as target_database;
select table_name,column_name,referenced_table_name,referenced_column_name
from information_schema.key_column_usage
where table_schema=database() and referenced_table_name is not null
order by referenced_table_name,table_name;

select pipeline_version,status,count(*) from script_analysis_task group by pipeline_version,status;
select result_format,status,count(*) from review_task group by result_format,status;
select scene,status,count(*) from ai_execution_task
where scene in ('script_element_extract','character_extract','scene_extract','prop_extract')
group by scene,status;
select operation_type,status,count(*) from script_ai_operation group by operation_type,status;
select count(*) from script_analysis_task task
where task.pipeline_version='LEGACY_V1'
and not exists (select 1 from script_analysis_stage stage join ai_workflow_agent_run run
  on run.analysis_stage_id=stage.id where stage.task_id=task.id)
and not exists (select 1 from ai_workflow_agent_run run
  where run.task_id=task.id and run.tenant_id=task.tenant_id and run.project_id=task.project_id
  and run.agent_code in ('short-drama-global-understanding','short-drama-episode-splitting',
    'short-drama-episode-summary','short-drama-asset-recognition'))
and not exists (select 1 from script_analysis_fanout_snapshot snapshot where snapshot.task_id=task.id);
select count(*) from review_task where coalesce(result_format,'')<>'MARKDOWN';
select count(*) from script_asset_candidate;
select count(*) from script_asset_normalization_run;
select count(*) from ai_agent_definition;
select count(*) from ai_skill_definition;
```

同时保存受影响租户的 team_point_account、reservation 和 point_ledger 快照，采集当前 Markdown task/report、正式 summary/asset、WorkflowAgent 主行与绑定、模型及价格配置的前后摘要。实际库每表删除数量尚未采集，不能用隔离测试库数量替代。

## 迁移后校验

```sql
select tenant_id,count(*) as releases,sum(amount) as released
from point_ledger where idempotency_key like 'migration:V114:reservation:%:release'
group by tenant_id;
select tenant_id,id,execution_id,reserved_points,settled_points,released_points
from ai_point_reservation
where reserved_points-settled_points-released_points<0;
select execution.id,execution.status,execution.retryable,execution.next_run_at
from ai_execution_task execution
where execution.scene in ('script_element_extract','character_extract','scene_extract','prop_extract')
and (execution.status in ('PENDING','RUNNING') or execution.retryable=true or execution.next_run_at is not null);
select account.tenant_id,account.reserved_balance,coalesce(reservation.remaining,0) as reservation_remaining
from team_point_account account
left join (
  select tenant_id,sum(reserved_points-settled_points-released_points) as remaining
  from ai_point_reservation where status in ('RESERVED','SETTLEMENT_REVIEW_REQUIRED')
  group by tenant_id
) reservation on reservation.tenant_id=account.tenant_id
where account.reserved_balance<>coalesce(reservation.remaining,0);
```

## 验证记录

迁移与 schema 回归 52 项通过。增补审核 run/step 取消后，7 项迁移回归再次通过，覆盖空库、混合库、重复清理、余额异常回滚、Agent/fanout 保护、无效当前 modelId、自定义 Agent 精确解绑、旧/新审核分片级联区分、scene_extract 独立执行、同业务再生成多 execution/version 和 FAILED retryable 调度清理。新增 run/step 用例同时在 MySQL 8.4.6 通过：旧 FORMAL 与快照 child run 取消；当前 Markdown、跨租户、跨项目、分析 run 和终结状态保留；未完成 step 取消且输入输出证据不变。

MySQL 8.4.6 空库及混合库已分别串行升级到 V115 并通过。混合库最终复核：余额 70→85、预占余额 30→15、已消费保持 10；新增 4 笔 RELEASE 合计 15，累计释放 2→17；8 条 execution、8 条 attempt、8 条 reservation 全部保留；当前分析任务 5 和 Markdown 任务 7 仍 RUNNING，当前 Markdown 片段与正式概要保留。默认与自定义审核 Agent 的管理员 prompt、model、status、revision 整行未变，仅精确退役的绑定删除。cleanup 连续调用两次后再由 Flyway 执行 V114，未重复释放。

首次双库同时存在的演练暴露原 V49/V57 的 information_schema 查询未限定 table_schema，会受同连接可见的其它业务 schema 干扰。历史迁移不修改，最终演练每次实例仅保留一个专用业务库；该既有部署限制需单独处理。通过后空演练库已删除，最终混合库保留供检查。

没有连接应用默认 3306 或实际业务库。仅重建本任务 127.0.0.1:33917 上明确授权的 ant_legacy_empty/ant_legacy_mixed 演练数据库，没有删除对象存储或导出文件。
