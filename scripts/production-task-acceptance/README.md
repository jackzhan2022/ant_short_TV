# 任务中心部署验收工具

这些脚本记录 2026-09-12 的一次验收，目标为 antv-prod、提交 6652857。应用结果和完整数据不下载，证据仅包含计划、耗时、行数、哈希和专用样本 ID。实际报告见 openspec/changes/archive/2026-09-12-add-production-task-center/verification.md。

这不是通用的一键发布工具。release_check.py 固定了新旧版本与上传文件哈希，并要求起始版本匹配；重复执行会拒绝已有发布目录。新的发布应使用部署手册并重新确认目标、哈希、备份、活动工作和验收范围。

## 读取与性能

从仓库根目录编译 ExportQueries.java 和应用的 ProductionTaskSources.java，再执行导出器：

```powershell
javac -encoding UTF-8 -d .temp/task-center-acceptance-classes backend/src/main/java/com/antshorttv/productiontask/ProductionTaskSources.java scripts/production-task-acceptance/ExportQueries.java
java -cp .temp/task-center-acceptance-classes com.antshorttv.productiontask.ExportQueries .temp/task-center-queries
```

将导出的 SQL 和 warnings.sql 放在部署主机验收目录的 task-center-queries 下，Python 文件放在其父目录。mysql_check.py 从服务器 shared/env 读取连接，密码只进入子进程环境；不打印配置。inventory.sql、preflight.sql 是只读诊断。benchmark.py 对真实表执行 EXPLAIN ANALYZE 和 7 次串行读取，写 performance.json。

scaled_benchmark.py 会在同一数据库建立唯一前缀 ptc_accept6652857_ 的 LIKE 表，生成 122,000 行合成数据；SQL 仅替换表名。不会复制业务正文，不会触发原表调度。finally 逐一删除本次实际创建的表，拒绝覆盖同名表。异常进程终止仍需人工核实精确前缀的残留，不要使用广泛 DROP 或清库。结果为 scaled-performance.json；这是有限串行测量，不是并发负载或吞吐基准。

## 发布与回滚

执行顺序（本次已执行完毕，不应重复）：

1. 依部署手册构建干净且已推送的提交，上传并核对 SHA-256。
2. http_smoke.py setup 经正常注册/登录 API 创建专用账号、团队和无剧本空项目；密码/cookie 仅存服务器 mode 0600 文件。
3. sudo python3 backup.py 备份数据库与共享 Skill；校验压缩包、完成标记和 tar 目录。不执行数据库恢复。
4. seed_legacy_fixture.py 在经过名称/ID 校验的专用团队中插入终态历史批次、子项、分析及固定结果；不发起 AI 调用，不写排队状态。http_smoke.py baseline 核验旧接口，snapshot.py baseline 保存数据库侧行哈希。
5. release_check.py new → http_smoke.py new → snapshot.py new。
6. release_check.py rollback → http_smoke.py rollback → snapshot.py rollback。
7. release_check.py restored → http_smoke.py restored → snapshot.py restored。
8. http_smoke.py close 停用专用团队并退出登录；finish.py 比较全部快照、停用精确匹配的验收账号、撤销其会话并删除两个测试凭据文件。样本留在停用团队，记录最终状态。

release_check.py 只在无活动执行和没有相关运行中领域任务时切换，保留旧版本。启动检查失败时回到切换前版本并重启，但仍需检查恢复结果。/api/auth/bootstrap 是真实认证检查端点；部署手册的 /api/currentUser 在匿名请求时虽返回 401，登录后实际为 404，不能用它验证已登录功能。

snapshot.py 在数据库内对完整行生成 JSON/SHA-256，只传输每行哈希；对已有周期性更新的 PENDING 分析记录仅比较关键状态。导出证据应逐个指定文件名，不要下载 private-*、env、数据库原始备份或整个服务器临时目录。
