# 角色、场景和道具识别参数修复发布

2026-10-19（北京时间）部署至 https://antv.aixmax.cn 。后端于 00:07:13 启动成功，进程 ID 为 1999381。

## 问题与修复

项目 49、分析任务 46 的前三级分析成功，70 集概要全部保存；资产识别累计 118 次调用因 HTTP 400 失败，错误为 `Unknown parameter: 'prompt_cache_options.assetPromptPolicy'`。

`AssetRecognitionAgentAdapter` 将内部资产范围 `assetScope` 和提示词策略 `assetPromptPolicy` 放入了供应商缓存参数。现将两者合并到 `trustedToolState`，保留 `fanoutSnapshotId`、`fanoutUnitAttemptNo` 等分集执行标识，向供应商传递的缓存选项保持为空。

## 验证

- 在独立的 HEAD 后端源码副本上应用本次两个文件的改动，运行 `AssetRecognitionAgentAdapterTest`、`ScreenplayToolDataServiceTest`、`AssetExtractionCoordinationTest`、`OpenAiAdapterTest`：83 项全部通过。
- 使用线上实际依赖编译适配类，再将该编译产物放入测试类路径执行适配器测试：5 项全部通过。
- 回归验证普通识别默认范围、限定角色范围、道具重新生成策略，以及分集执行标识的保留；供应商缓存参数均不含内部控制字段。
- 发布后服务 active；首页和项目剧本页面返回 200；匿名当前用户 API 返回预期 401。Flyway 校验通过，未执行新迁移。

## 发布产物与回滚

此次直接以线上已有发布包为基础，仅替换经过验证的 `BOOT-INF/classes/com/antshorttv/script/AssetRecognitionAgentAdapter.class`，逐项比较 ZIP 内容确认只有该类变化。保留线上其他功能、前端与资源，以避免将工作区中其他进行中的改动带入本次发布。

- 当前发布：`/opt/antv/releases/20261018-asset-cache-fix`
- 上一发布：`/opt/antv/releases/20261018-tenant-trial-37d9df9`
- 原 JAR SHA-256：`3f01ce6d10da477cb59c01af356340f6f50171ce52e725bbd87e29f2553c8f5d`
- 新 JAR SHA-256：`7bb21f3b27679609455669137623f5625d6e8945b3300b03a9df8b06eb9bce32`
- 当前发布目录中的 `asset-cache-fix-manifest.json` 记录产物核对结果，`source-fix.diff` 保存源码及测试改动。
- 数据库与工作流 Skill 的配套备份：`/opt/antv/backups/20261018-asset-cache-fix`；gzip 完整性、数据库导出完成标记和 Skill 归档可读性均已验证。

服务器发布前磁盘可用空间不足。核对 SHA-256 后，仅删除四份已有保留发布版本副本的临时 JAR，释放 547,674,022 字节；所有正式发布和备份均保留。明细位于 `/home/ubuntu/antv-asset-cache-fix-disk-cleanup.json`。

应用回滚可将 `current` 原子切回上一发布并重启服务；本次没有数据库结构改动。回滚到旧代码后，不应继续重试受此参数问题影响的资产识别任务。

## 项目 49 恢复

旧执行 178541 已取消，但业务任务及资产阶段仍遗留 RUNNING 状态。以任务 46、阶段 184、租户 1、项目 49、原执行 178541 和当前剧本版本为条件，原子修正这两条记录为可重试失败状态；原值保存在备份目录的 `project49-before-state-repair.txt`，未改写原执行或历史积分账务。

用户完成登录后，通过页面“重试此步骤”正式发起重试，服务创建新执行 178542。只重试资产识别，保留全局理解、分集与 70 集概要。

截至 2026-10-19 00:11:28：

- 资产识别已成功 6/70 集，4 个分集 Agent 正在执行，新的 Agent Run 尚无失败。
- 已保存 8 个角色、3 个场景、12 个道具；既有自动分镜流程已成功处理 2 集。
- 原来的参数 HTTP 400 未再出现，剩余剧集由后台任务继续处理。

恢复验证截图：`D:/信计软件项目/ant_short_TV/.temp/project49-asset-recognition-recovered.jpg`。
