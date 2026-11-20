# 新禾文化积分账户死锁确认

## 结论

已通过真实 MySQL 8.0.46 并发实验确认：`PointAccountingService.lockAccount()` 中，先运行 `INSERT ... SELECT ... WHERE NOT EXISTS`，再运行 `SELECT ... FOR UPDATE`，在 `REPEATABLE READ` 下会对同一已存在账户产生共享锁升级死锁。

该路径存在于线上实际 JAR，复现失败的 SQL 和错误类型与项目 53 的概要失败一致。线上 13:32:40 的历史完整锁等待链未取得，故实验不证明当时另一个事务的具体身份，也不排除其他死锁路径。

## 线上证据

- 租户：新禾文化，tenant_id=37；项目：53；分析任务：49。
- 概要 Run 3782 于 13:32:40 失败。记录中的 SQL 为 `select tenant_id from team_point_account where tenant_id=? for update`，错误为 `Deadlock found when trying to get lock; try restarting transaction`。
- 资产识别 Run 3783 同时于 13:32:40 开始，之后成功。仅凭同时开始不能认定它就是死锁中的另一个事务。
- 数据库为 MySQL `8.0.46-0ubuntu0.24.04.3`，默认事务隔离级别 `REPEATABLE-READ`；表为 InnoDB，tenant_id 有唯一索引 `uk_team_point_account_tenant`。
- 从当前发布 `/opt/antv/releases/20261020090459-faa6a07-analysis-retry` 读取实际 JAR 的类文件，使用 `javap` 核对：`lockAccount()` 确实先调用 `ensureAccount()`，随后执行出错的 `FOR UPDATE`；`ensureAccount()` 包含原初始化 SQL。相关源码与 faa6a07 一致。
- 线上应用账号缺少 PROCESS 权限，不能读取 `SHOW ENGINE INNODB STATUS`。线上 `innodb_print_all_deadlocks=0`，未变更该设置或数据库权限。

## 本次恢复方式

恢复来自执行调度器的自动重试。首次执行尝试 1731 于 13:34:35 结束，持久化 `retryable=1` 和 `next_retry_at=13:34:40`；第二次尝试 1733 于 13:34:41 开始，13:34:56 成功，`retry_count=1`。对应时间段 Nginx access.log 无该任务或项目的 retry 接口请求。

剧本分析执行器的现有重试策略是最多 3 次尝试（含首次），失败轮次结束后等待 5 秒进入下次调度。本次概要先于资产分支失败，整轮等待资产分支完成后才结束，因此不是在 13:32:40 立即重试。第二轮概要 attempt=2，成功资产阶段仍是 attempt=1。

该机制重试分析执行轮次；本报告建议的账务短事务死锁重试尚未实现。诊断过程未发起线上业务重试。证据见 `outputs/xinhe-retry-origin-20261020.jsonl`。

## 隔离复现

使用应用主机上独立的 `mysql8` 测试实例（MySQL 8.0.46）。该实例与生产数据库地址不同。每次创建带随机后缀的专用测试库，使用线上账户表结构和虚构账户；实验结束删除专用测试库。

两个连接分别开启事务，先各执行原初始化 SQL，再同步申请同一账户的排他锁。两个连接执行完初始化 SQL 后，`performance_schema.data_locks` 显示均持有索引 `uk_team_point_account_tenant`、同一记录 `37, 1` 的 `S,REC_NOT_GAP` 锁。

InnoDB 死锁报告显示双方均持有该记录的 S 锁，并等待该记录的 X 锁，随后其中一个事务被回滚，返回 `ERROR 1213 (40001)`。

| 测试路径 | 隔离级别 | 轮数 | 死锁轮数 | 其他错误 |
| --- | --- | ---: | ---: | ---: |
| 原初始化 SQL → FOR UPDATE | REPEATABLE READ | 5 | 5 | 0 |
| 直接 FOR UPDATE | REPEATABLE READ | 5 | 0 | 0 |
| 原初始化 SQL → FOR UPDATE | READ COMMITTED | 5 | 0 | 0 |

测试仅执行初始化判断和账户锁定，没有调用模型或进行扣费。测试账户余额始终为 10000，测试库已清理。READ COMMITTED 组用于验证共享锁机制，不表示建议修改生产全局隔离级别。

## 修复方向与边界

已有账户应直接获取排他锁，避免初始化查询先引入共享锁。账户不存在时需单独设计和验证并发创建分支，不能把此次“已有账户”的对照实验当作完整修复验收。

后续如增加 1213 兜底重试，应完整回滚后重开短账务事务，并复用原幂等键；不能重试整个模型调用流程。本次仅完成诊断和对照实验，尚未修改或发布生产代码。

## 本地证据文件

- `outputs/xinhe-runs-20261020.txt`：线上 Run 和执行尝试。
- `outputs/xinhe-lock-settings-20261020.txt`：线上 MySQL 版本、隔离级别、账户表结构。
- `outputs/xinhe-lock-reproduction-5rounds-20261020.json`：15 轮实验、持锁快照、死锁报告、对照结果与清理结果。
- `outputs/xinhe-deployed-classes-20261020.json`：从线上 JAR 读取的相关类及哈希。
- `outputs/xinhe-deployed-account-bytecode-20261020.txt`：账户服务字节码。
- `.temp/xinhe-lock-reproduce.py`：真实 MySQL 隔离复现实验脚本。
