# 单链路发布与恢复

本次开发只修改代码和迁移，并在隔离库演练。以下操作针对实际目标环境由部署时执行；历史可清理不等于删除全部项目或财务审计。

## 执行顺序

1. 记录实际主机、端口、数据库名、版本、发布提交和 `select database()` 输出；按 data-cleanup-manifest.md 采集范围数量、FK 与财务余额。MySQL 空库安装需核对历史 V49/V57 的跨 schema 探测限制。
2. 停止所有旧版本 API、共享 worker、视频/拆解 scheduler 和自动分镜消费者，确认没有旧进程继续写入。调度启停由进程生命周期控制，不配置新旧链路开关。
3. 创建数据库一致性快照及匹配的文件 Skill 根目录快照；记录位置、时间、校验和，验证备份可读取。保留当前模型、Agent 管理员配置及共享审计的校验摘要。
4. 运行新发布版本的 Flyway：V114 在同一事务内取消旧执行、释放剩余预占并追加账本，之后清理旧业务；V115 删除专用结构。不要另跑手写退款或重复释放 SQL。
5. 按清单执行迁移后 SQL，确认旧任务无法再领取、无负预占及孤立引用；对照前后当前数据/配置摘要并记录实际影响数量。
6. 先由一个新后端完成迁移和幂等 bootstrap，确认模型支持工具调用、当前 Agent/Skill 有效；随后启动其余新实例和消费者。缺少配置应给出可诊断错误，不切回旧实现。
7. 使用受控提供者验证轻量工作台、分析双分支、自动分镜、单类型重提取、QUICK/DEEP Markdown 和取消/重试。核对积分预占与结算；真实付费调用另按部署测试范围执行。

## 失败恢复

V114 事务失败时检查实际 Flyway 状态及账本，修复不一致后重试，不人工补余额。V115 是 MySQL DDL，不能依赖事务回滚。跨 V114/V115 回退必须停止全部写入，恢复匹配数据库和 Skill 快照，再启动匹配旧应用；仅回退二进制不安全。禁止通过补回旧列、增加调度开关或混跑新旧 worker 规避恢复。

## 文件与对象存储

本次没有删除任何导出、上传文件或对象键，也没有授权清理整个桶或目录。后续若清理被删历史的导出文件，先导出精确 object key 清单，逐项验证没有当前导出/媒体/日志引用，再执行该清单；不使用前缀通配删除。当前精确删除清单为空。

## 影响报告模板

```text
release_commit:
target_host_port_database:
started_at / finished_at:
database_snapshot / skill_snapshot / checksums:
flyway_before / flyway_after:
legacy_analysis_tasks_removed:
legacy_review_tasks_removed:
retired_executions_canceled / retries_disabled:
agent_runs_canceled / steps_canceled:
reservations_released / release_ledger_rows / released_points:
retired_table_rows_before_drop:
permission_bindings_removed / tool_skill_bindings_removed:
current_model_agent_skill_checksum_before_after:
retained_execution_attempt_reservation_counts_before_after:
negative_reservation_count / orphan_reference_count:
object_keys_deleted: []
verification_results:
recovery_performed:
```
