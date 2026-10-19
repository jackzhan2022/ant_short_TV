# 分镜保存死锁优化发布

2026-10-19（北京时间）发布至 antv.aixmax.cn。服务于 18:39:52 进入 active，健康检查 UP，自动重启次数 0。

## 实现与验证

- 功能提交 `d23f7efdf68bd4213a5b56b11f0dfbb1fb51164a`，已推送 master。新分镜直接插入素材引用；已有引用编辑和分镜替换仍保留原有退役逻辑。
- 保存工具新增独立事务边界，仅对 MySQL 死锁码 1213 最多尝试 3 次；每次完整回滚后创建新事务，复用原始生成参数，不重复请求模型。保留期限和中断处理，内容、权限、重复键、普通锁超时与连接故障不重试。
- 204 项本次相关测试最终通过，包含事务回滚、事务隔离、参数副本、边界次数、无模型调用、素材锁定、并发绑定、Agent 与计费流程。额外修正已在未修改版本复现的冷启动测试夹具，生产启动与模型选择逻辑未改。
- Biome、TypeScript、antd 检查通过，保留既有警告。后端从已推送提交独立构建，1,262 个应用类与测试版本一致，JAR SHA-256 为 `753e4450399536a0b6842bade369cdabde8be3c12fd1985f92e2f7ee594ebfd6`。
- 发布目录 `/opt/antv/releases/20261019183117-d23f7ef-storyboard-save-deadlock`；上一发布 `/opt/antv/releases/20261019172814-4cbe41f-asset-detail-fallback` 完整保留用于回滚。前端复用原有静态资源，数据库 V140 无新增迁移，33 个技能文件、环境、Agent 和模型参数配置哈希/快照保持一致。

## 备份与保留

数据库一致性备份流式保存到本机 `D:\需求文档\ant_short_TV\.release\20261019182320-storyboard-save-deadlock-backup\database.sql.gz`，两端 SHA-256 和压缩包完整性校验通过。服务器备份 `/opt/antv/backups/20261019182320-storyboard-save-deadlock-backup` 保留技能、环境、配置快照与验证清单。

更旧的 e8260a1 发布 JAR 已有本机完整副本并核对哈希；确认非当前/上一发布且无进程使用后，仅归档移除该服务器 JAR，释放约 131 MiB。归档清单保存在其发布目录，当前和上一发布均完整保留。未清理业务数据、上传文件或数据库备份。

## 第 57 集线上验收

确认原来没有已保存分镜后，经现有登录页面确认预计消耗 0 积分，于 18:44:53 发起生成。新执行 178685、Agent 运行 3515 于 18:46:34 成功结束，保存 8 条分镜、45 条素材引用，45 条形态引用均有效。本次为正常发起的一次业务生成；事务自动重试机制不会额外请求模型。本次运行记录为 1 个模型步骤、1 个保存工具步骤，共 7 步。

截至 18:47:00，项目概要、资产识别和分镜均覆盖 70/70 集，共 572 条正式分镜，无运行中项目任务。历史失败执行 178649 保留作为审计记录，缺失结果已由新执行补齐。

工作台阶段累计实际净扣费 1000.00000000，本次新增净扣费 0，预占 0；口径为 `tenant_id=39 AND workbench_project_id=50` 的 `settled_points-refunded_points`。

完整记录见本机 `outputs/storyboard-save-release-online-verification.json`、`outputs/storyboard-save-release-verification.json` 和 `outputs/project50-storyboard57-deadlock-fix-success.png`。历史完整死锁锁链无读取权限，本次不把空范围锁风险推断表述为已证实的唯一根因。
