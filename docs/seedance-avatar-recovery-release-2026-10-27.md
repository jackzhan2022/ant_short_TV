# Seedance 真人相似自动恢复与素材容量管理上线记录

- 发布时间：2026-10-27T12:05:26.547853+08:00（Asia/Shanghai）。
- 代码提交：`f170aee95bfc0f46590c557c7f71a9d8d9639d69`，已合入并推送 `master`。
- 发布目录：`/opt/antv/releases/20261027115711-f170aee9-avatar-recovery`。
- 上一版本：`/opt/antv/releases/20261027104840-e266b040-asset-image-download`，保留用于回滚。
- 备份目录：`/opt/antv/backups/20261027115711-f170aee9-avatar-recovery`，数据库压缩备份 120,347,679 字节，配置和工作流文件均已备份并校验。

## 上线内容

明确的 Seedance 输入图片真人相似拒绝会启动当前分镜的持久化恢复流程：按需入库候选人像、等待 Active、使用冻结的原始输入创建唯一关联视频任务。重复失败不无限恢复，前端展示阶段，后台继续执行。

素材与素材组分别按账号 50 的上限管理，AIGC/LivenessFace 一起计数；每五分钟和入库前盘点，达到 45 时尽力回收至 40。整批准入防止多分镜互相占住部分容量；仅回收系统托管且没有在用消费者的远端素材，保留源图及历史结果。

## 生产配置与迁移

- 通过 IAM ListProjects 完整核对项目清单，当前只有 `default`。
- `ARK_ASSET_PROJECT_NAMES=default`。
- `ARK_ASSET_QUOTA_SCOPE=antv-production-ark`。
- 自动恢复、定时盘点和清理已启用。
- 原 AK/SK、模型与价格配置、共享工作流文件保持一致。
- 原有 158 份迁移资源哈希全部一致；仅新增 `V164__seedance_avatar_recovery_and_capacity.sql`，已成功执行。
- 现有系统音频库、分镜图片上传、原图下载与批量 ZIP 下载随最新主干保留。

## 验证

- 发布前：84 项后端测试、172 项前端测试通过；全量 Biome、TypeScript、antd lint 退出码为 0。
- 线上服务：`active`，健康 `UP`，进程无自动重启。
- 主页 HTTP 200；6 个前端公开资源均返回 200，SHA-256 与发布包一致；在外部网络再次核对通过。
- 新恢复查询接口：登录态 HTTP 200，匿名 HTTP 401，跨项目与跨团队 HTTP 403。GET 未创建恢复任务，临时验证会话已移除。
- 定时盘点已实际运行：`2026-10-27 12:04:51`，5 份素材、2 个素材组。
- 原图下载 2 份、批量 ZIP 下载 2 项通过，归档字节与原图一致；CSRF、跨项目、匿名访问检查通过。
- 验证未发起付费视频生成，也未通过测试接口上传或删除火山素材。

## 原目录本地改动保留

合并前原目录基线为 `56d067b`。6 个已修改文件与 14 个冲突或重复迁移的未跟踪文件完整保存在 stash `b44a24a2f9a443ff501a131484b563a8843099fd`。

6 个已修改文件和另外 4 个新增文件属于原图/批量下载的本地版本，正式版本已在主干；4 个文件是历史迁移草稿；6 个文件为本次 OpenSpec 的旧方案副本。旧草稿未作为额外功能或迁移重复发布。

备份清单：`.release/seedance-avatar-recovery-20261027/master-workspace-preservation.json`。

发布回执与外网核验：`.release/seedance-avatar-recovery-20261027/deployment-receipt.json`、`public-verification.json`。
