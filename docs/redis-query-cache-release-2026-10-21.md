# Redis 查询缓存发布记录（2026-10-21）

## 发布范围

基于远程 master `78224a2`（后端、前端源码与线上消息中心 `fbdbe15` 一致）提取 Redis 第一期及作用域失效补丁。仅发布后端，沿用线上前端，数据库迁移文件保持一致。

五类缓存：任务统计 2 秒、分析进度 2 秒、版本/剧集导航 60 秒、消息未读 5 秒、公共模型选项 120 秒。权限检查保持实时，失效按用户、租户、项目执行，Redis 故障回源 MySQL。

## 发布前验证

- 144 项本地后端回归通过（包括缓存开启的完整应用启动），无失败、错误或跳过。
- 2 项原始 Lettuce 集成用例在数据库服务器独立回环 Redis 上通过：认证、读写、TTL、越权/超限响应拒绝，以及冷连接故障并发等待上限。临时 Redis 为 64 MiB，测试后停止。
- 首次通过公网 SSH 隧道运行 Redis 测试触发 200ms 握手超时；改为服务器回环验证，没有放宽生产客户端超时。
- 修正一处已有测试夹具：归一流程已预加载清单、仅允许单次保存，mock 不再额外返回读取工具调用。原失败已在未修改基线复现，生产归一逻辑未变。
- 前端 `npm run lint`、`npx antd lint ./src` 通过；既有警告保留。
- 独立代码及发布脚本复核通过。
- 发布前五类认证接口读请求正常；临时测试会话已删除。
- 完成数据库、环境和 33 个工作流 Skill 文件备份校验。备份目录 `/opt/antv/backups/20261021-redis-cache`。

## 发布与回退

生产原版本：`/opt/antv/releases/20261021103439-fbdbe15-message-center`。

部署使用版本目录及原子链接切换；短暂暂停 API 入口并排空活动任务。通过独立 systemd drop-in 加载 `/opt/antv/shared/redis.env`，设置 `QUERY_CACHE_ENABLED=true`。启动或认证缓存验收失败时撤销该 drop-in、切回原版本并恢复服务。

仅关闭缓存时，将 `/etc/systemd/system/antv.service.d/query-cache.conf` 中 `QUERY_CACHE_ENABLED` 改为 `false`，再执行 daemon-reload 和受控服务重启。无需清空共享 Redis。

## 首次发布回退与修复

首次尝试版本 `6d0c850` 在缓存启用时启动失败：全包 `@MapperScan` 将 `CacheStore` 接口注册为 SQL Mapper，与真实 Redis CacheStore Bean 冲突。发布脚本健康检查失败后自动切回消息中心旧版本，撤销缓存配置并恢复外部访问；数据库迁移未改变。

新增 `QueryCacheEnabledApplicationTest`，在真实应用上下文中开启缓存，先复现重复 Bean 错误。将 Mapper 扫描限定为 `@Mapper` 注解接口；全量接口检查确认 67 个实际 SQL Mapper 均已注解。测试还验证真实数据库 Mapper 查询及 Redis 故障回源。

发布脚本同时完善停机期间的回退标记与重启计数处理。修复后上线结果将在部署验收后补充。测试/两次请求的数据不能作为生产 SQL 总负载或延迟下降比例。
