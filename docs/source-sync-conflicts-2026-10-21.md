# 源码同步与本地改动冲突处理（2026-10-21）

## 线上核对

- 生产站点：`https://antv.aixmax.cn`。
- 实际版本：`/opt/antv/releases/20261021192505-7b0f114-review-history-tool`。
- 发布清单与部署回执的 revision 均为 `7b0f114f281b1eb131ddf57d96b3dfead551111e`，与当前 master 一致。
- 服务 active，健康接口 UP，NRestarts 为 0；公网首页正常，未登录访问 currentUser 返回 401。
- 最新发布以不重启方式完成 Flyway 迁移；回执确认运行中应用条目逐字节一致，进程启动于上一版 `20261021165308-24cb97d-text-only-video`，前端未变。
- 当前后端 JAR SHA256：`2d1ca3771be1b8566ee2309ea652a83a53021b72e5716b6f8a54756f068a512a`。
- 当前前端 index.html SHA256：`747a52a4026feee0b75b2e81acd6c57e3c394e15d91ca18e095b43c6a68bbaec`。
- 核对仅读取生产发布信息、服务状态、公网页面及共享技能元数据；未部署本次本地改动。

## 处理原则与结果

备份为 `stash@{0}`，对象 ID `a6d5c353a1396445c2ec1caf0af55d3d1d98270b`，名称 `codex-before-pull-2026-10-21`。备份保留，不直接重新应用整份旧补丁。

保留线上已经发布的新实现，逐项恢复尚未被远端包含且兼容的本地改动。22 个合并冲突中，19 个文件采用当前 master，3 个文件组合合并；另处理了 26 个新增文件同名问题。当前工作区没有未合并条目或冲突标记。

恢复的本地功能包括：视频与首帧预演连续播放、分镜卡片网格布局、按当前分集显示分镜生成等待/运行状态、剧本页优先显示项目名称，以及 GPT-6 Astra 路由和 reasoning_effort 测试。

保留线上分镜页的视频/首帧/场景预览切换、纯文本视频生成、缺图确认、积分预估、自动归一与分类型并发池，不用旧实现覆盖这些行为。

24 个本地技能名称和描述改动已恢复，正文仍采用远端最新内容。21 个仍在生产共享目录中的技能元数据与线上一致（审核执行框架的描述采用线上版本）；其余 3 个为共享目录中已不存在的旧分镜技能，仅恢复本地元数据，不重新启用或发布。

## 22 个合并冲突的决定

以下路径相对于仓库根目录。

| 文件 | 处理 |
| --- | --- |
| backend/src/main/java/com/antshorttv/aiimage/AssetImageBatchService.java | 保留线上资产归一 guard。 |
| backend/src/main/java/com/antshorttv/commercial/CommercialEntitlementOrchestrator.java | 保留线上套餐履约结构，不恢复已移除的旧订阅实现。 |
| backend/src/main/java/com/antshorttv/execution/AiExecutionClaimService.java | 保留线上按任务类型配置的团队与模型并发准入。 |
| backend/src/main/java/com/antshorttv/execution/AiExecutionDispatcher.java | 保留线上分类型执行池和领取机制；排除自动合并产生的重复发布查询方法。 |
| backend/src/main/java/com/antshorttv/execution/AiExecutionExecutorConfiguration.java | 保留线上删除状态，使用新的执行池配置。 |
| backend/src/main/java/com/antshorttv/points/AiPointReservationEntity.java | 保留线上工作台项目和积分预算字段。 |
| backend/src/main/java/com/antshorttv/points/AiPointSettlementService.java | 保留线上项目预算、试用积分与共享预算释放。 |
| backend/src/main/java/com/antshorttv/points/PointAccountingService.java | 保留线上账户锁定及资金预留实现。 |
| backend/src/main/java/com/antshorttv/productiontask/ProductionTaskService.java | 保留线上资产自动归一任务类型及权限映射。 |
| backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java | 保留线上资产归一协调、已提交调用复用与模型调用统计。 |
| backend/src/test/java/com/antshorttv/execution/AlwaysOnAiSchedulingTest.java | 保留与新执行池一致的测试配置和查询顺序。 |
| backend/src/test/java/com/antshorttv/script/ScriptWorkflowControllerTest.java | 保留线上归一流程对应的调用次数及积分断言。 |
| frontend/src/access.ts | 保留线上平台管理员发放套餐权限。 |
| frontend/src/app.test.tsx | 保留线上入口测试 mock 配置。 |
| frontend/src/app.tsx | 保留线上系统监控组件。 |
| frontend/src/pages/commercial-management/tenants/index.test.tsx | 保留线上管理员套餐发放测试。 |
| frontend/src/pages/commercial-management/tenants/index.tsx | 保留线上套餐发放入口、列宽和积分格式。 |
| frontend/src/pages/commercial/index.tsx | 保留线上统一积分格式。 |
| frontend/src/pages/projects/production-workbench/StoryboardAssetReferenceEditor.tsx | 保留线上素材选择器逻辑，不恢复重复的悬停加载处理。 |
| frontend/src/pages/projects/production-workbench/script.tsx | 保留线上分析进度样式，并恢复本地项目名称显示。 |
| frontend/src/pages/projects/production-workbench/storyboard.tsx | 保留线上预览与视频提交逻辑，合入本地分集生成状态隔离及等待/运行提示。 |
| frontend/src/pages/projects/production-workbench/storyboard.test.tsx | 保留线上纯文本、缺图及预览测试，追加本地分集生成状态测试。 |

## 新增文件同名问题

原未跟踪文件有 26 个已在最新 master 中被跟踪，其中 14 个内容完全一致，无须覆盖。

剩余 12 个不同内容文件按下表处理：

| 文件 | 处理 |
| --- | --- |
| backend/src/main/java/com/antshorttv/points/AiPointEstimateCalculator.java | 保留线上工作台折扣。 |
| backend/src/main/java/com/antshorttv/points/AiPointEstimatePlan.java | 保留线上项目预算和工作台计划字段。 |
| backend/src/main/java/com/antshorttv/points/AiPointEstimateService.java | 保留线上项目累计限额、资产归一预估和租户技能上下文。 |
| backend/src/test/java/com/antshorttv/execution/AiExecutionDispatcherTest.java | 保留新执行池测试。 |
| backend/src/test/java/com/antshorttv/points/AiPointEstimateCalculatorTest.java | 保留线上折扣测试。 |
| backend/src/test/java/com/antshorttv/points/AiPointEstimateServiceTest.java | 保留线上预估、项目上限与归一测试。 |
| docs/ai-point-estimation.md | 保留与最新计费实现一致的文档。 |
| frontend/src/pages/projects/production-workbench/StoryboardVideoEmptyState.tsx | 合入本地等待/运行状态及无障碍状态提示。 |
| frontend/src/pages/projects/production-workbench/StoryboardVideoStrip.tsx | 保留线上版本条及单视频预览行为。 |
| frontend/src/services/point-estimate-dialog.test.tsx | 保留线上错误信息与积分显示测试。 |
| frontend/src/services/point-estimate.test.tsx | 保留线上资产归一积分预估覆盖。 |
| frontend/src/services/point-estimate.tsx | 保留线上统一积分格式、资产归一匹配与错误信息提取。 |

其余原未跟踪文档、素材、脚本和压缩包保留原状。

## 验证

- 前端 5 个测试文件、138 项测试通过，覆盖播放器、视频工作台、分镜及积分预估。
- `npm run lint`（Biome + TypeScript）通过，保留原有 3 条 document.cookie 提示。
- `npx antd lint ./src` 通过，保留原有 10 条静态反馈 API 提示。
- 后端 13 个测试类、54 项测试最终通过，覆盖模型路由、OpenAI 适配器、技能契约、执行池准入和积分预估。
- 首轮发现资产技能契约仍要求旧文案“身份敏感字段”；确认该断言在远端最新正文中也不成立（正文由 `00c96a1` 更新，测试未同步），将断言更新为现有性别、年龄及国籍判断规则；该类 3 项测试复测全部通过，未修改技能正文。
- 最终审计：未合并文件 0、冲突标记 0、19 个采用远端的文件逐项一致、21 个在用技能元数据与线上一致，原始 stash 保留。
- 测试日志及本次临时合并材料：`.temp/source-sync-20261021/`。

## 推送前同步远端更新

推送前远端新增 `d9cff47`（分镜编辑器加载与批量读取性能优化）。本地提交在该版本上重新应用，交叉冲突仅涉及分镜页当前剧集的计算：保留远端按项目、稳定剧集 ID 加载详情与取消过期请求的逻辑，再组合本地按集隔离的生成状态。其余本地改动自动合并。

组合后补测前端 6 个文件、160 项测试全部通过，包含视口挂载、分镜页、播放器、视频工作台和积分预估；Biome、TypeScript 与 antd 检查再次通过，保留原有提示。上述后端 54 项结果为此前冲突处理阶段的验证，本次交叉冲突与手工调整只涉及前端。
