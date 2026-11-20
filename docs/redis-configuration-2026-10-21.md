# Redis 服务端配置与接入准备

时间：2026-10-21，北京时间。用户授权配置现有 Redis，目标是为短剧项目准备受限账号、内存预算和服务器间连接。

## 最新调整：Redis 内存上限提高到 2 GiB

2026-10-21 10:58（北京时间），用户授权扩大 Redis 容量。复查服务器为 8 核、15483 MiB 内存，MemAvailable 约 10167 MiB，Swap 基本未使用；主机同时运行 MySQL、达梦数据库等服务。

- 仅将 `/etc/redis/redis.conf` 的 `maxmemory 512mb` 改为 `maxmemory 2gb`，运行时确认 **2147483648 字节**。
- 保持 `noeviction`、现有 ACL、认证、网络及持久化配置。上限作用于整个共享 Redis 实例，不是短剧项目独享配额，也不是进程 RSS 的严格上限。
- 这是按需使用的容量上限，不预分配 2 GiB；验证时 Redis 进程 RSS 为 13.38 MiB。主机须继续给 MySQL、其他服务、系统缓存及 Redis 自身开销留余量，本次没有对这些服务做硬性内存预留或调整。
- 候选配置先在独立 Unix socket 实例验证，确认生产无业务客户端连接后备份、重启 Redis。未重启 MySQL 或应用后端。
- 从应用服务器验证：认证、读写、TTL、跨前缀读写拒绝、CONFIG 命令拒绝、测试键清理及 2 GiB/noeviction 全部通过；后端健康检查 UP。
- 应用服务器 `/opt/antv/shared/verify-redis-connection.py` 已更新为校验 2 GiB。
- 本次备份：`/var/backups/antv-redis/20261021105828-memory-2g`。其中 `redis.conf.before` 对应此前 512 MiB 配置，包含已有 ACL 和凭据哈希；需要回退此次容量调整时，应使用该版本而非下文最初配置前的备份，并先确认没有后续配置变更和在途任务。
- 本地证据：`outputs/redis-memory-2g-receipt-20261021.json`、`outputs/redis-memory-2g-verification-20261021.json`。

以下为此前 512 MiB 初次配置与验证记录；当前容量以上述 2 GiB 为准。

## 已完成

- Redis 服务器：43.136.47.235；内网地址：172.16.0.10；端口：6379；版本：7.0.15。
- 应用服务器：43.138.147.3；内网地址：172.16.0.16。
- Redis 全局 maxmemory 设置为 536870912 字节（512 MB），maxmemory-policy 设置为 noeviction。达到上限后，需额外分配内存的写入会报错，不自动淘汰已有协调记录。此上限和策略作用于整个共享实例。
- antv_app 限定键名 antv:*、发布订阅频道 antv:*；保留原密码哈希，另外生成新的高熵凭据供本项目使用。
- 权限基于原 +@all -@dangerous，额外允许 INFO 用于检查；跨前缀读写和 CONFIG 命令拒绝。
- default 账号仍关闭；smart-community-platform 账号配置原样保留。后者原有 ~* 权限仍可覆盖所有前缀，因此这不是双向租户安全隔离；不能向共享 Redis 写入依赖这种隔离的敏感数据。
- AOF 保持原有关闭状态，RDB 配置未改动。任务和账务仍以 MySQL 为准。
- 新凭据在数据库服务器保存于 /etc/redis/antv-app-connection.json，root 所有、0600；通过 SSH 输入输出管道传至应用服务器，全程未打印密码或落盘到本地工作区。
- 应用服务器连接参数保存于 /opt/antv/shared/redis.env，ubuntu 所有、0600，使用 SPRING_DATA_REDIS_* 命名，另有 ANTV_REDIS_KEY_PREFIX=antv:。

现有后端没有 Redis 客户端依赖。本次未修改业务代码、未让后端加载此文件，也未重启后端；连接配置就绪不等于缓存或分布式限流已接入。

## 已执行验证

先以候选配置启动独立、空数据、仅 Unix socket 监听的临时 Redis，验证配置可启动和 ACL 行为后退出。然后确认生产 Redis 没有已建立客户端连接，备份原配置并重启 Redis 服务。

生产 Redis 本机验证通过：认证 PING、antv:* 临时键 SET/GET/TTL/DEL、跨前缀读取拒绝、CONFIG 命令拒绝、INFO 返回实际 maxmemory=536870912 和 noeviction。临时测试键删除完成。验证时 used_memory_human=1.04M、db0 保留 1 个无过期键、rdb_last_bgsave_status=ok、aof_enabled=0。

## 已完成：应用服务器至 Redis 的网络连接验证

调整前，应用服务器访问内网 172.16.0.10:6379 在 TCP 建连阶段超时；内网 22 端口可达，ARP 邻居可达。用户确认添加指定安全组规则后，2026-10-21 10:49:31 从应用服务器执行验证，连接及全部功能检查通过。

现有云凭据调用 cvm:DescribeInstances 返回 UnauthorizedOperation，无法读取实例安全组标识或修改云端规则。用户确认已在腾讯云添加以下入站规则；本次独立验证了连通性，未通过云端 API 复核规则范围：

- 地域：ap-guangzhou。
- 实例：ins-otmq9iu8（43.136.47.235）。
- 协议及端口：TCP 6379。
- 来源：172.16.0.16/32。
- 动作：允许。

从应用服务器运行以下命令可复验：

```bash
python3 /opt/antv/shared/verify-redis-connection.py
```

脚本从私有环境文件读取凭据，只输出检查结果。实测全部通过：未认证请求拒绝、账号认证、PING、带 60 秒 TTL 的临时键读写、跨前缀读写拒绝、CONFIG 拒绝、512 MB/noeviction 实际内存策略及测试键清理。服务端配置和跨服务器接入准备现已完成，业务代码接入仍不在本次变更范围内。

## 备份与回退

数据库服务器备份目录：/var/backups/antv-redis/20261021104449-configure，包含 redis.conf.before、redis.conf.candidate 和 receipt.json，仅 root 可访问。

若需要回退，先检查是否有客户端和后续配置变更；确认可重启后恢复此次原配置，再重启并检查服务：

```bash
sudo cp --preserve=mode,ownership /var/backups/antv-redis/20261021104449-configure/redis.conf.before /etc/redis/redis.conf
sudo systemctl restart redis-server
systemctl is-active redis-server
```

回退会取消新增密码和内存预算，恢复原 antv_app 权限。配套连接文件使用的新密码随后不再有效。不要在未检查后续变更的情况下执行回退。

本地无密码证据：outputs/redis-configuration-receipt-20261021.json、outputs/redis-connectivity-verification-20261021.json。
