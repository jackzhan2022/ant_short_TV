# 分镜工作台性能优化与本地验收

日期：2026-10-21（Asia/Shanghai）。

## 当前交付状态

性能优化现已安全合入主目录，保留资产归一化、分镜导航、媒体历史及批量生成。最新主目录回归为前端146项、后端45项，本地20次P95为1.26秒；未提交、未部署。

当前验收详见 docs/storyboard-safe-merge-verification-2026-10-21.md。以下保留首次隔离实施的历史记录，其中当时“未合入”的状态已由本轮安全集成解决。

## 实施内容

- 新增 storyboard-editor-page：当前脚本、活动剧集的分镜分页；MySQL 在返回前投影编辑所需的 shotPlan 字段，保留提示词节点、引用和现有诊断字段。普通导航不再读取完整剧本或全部剧集概要。
- 新增 storyboard-episodes/{episodeId}/details：单独读取当前集概要与正式概要，不回传正文。原完整工作区接口仍保留。
- 新增项目 metadata：沿用项目权限，排除初始剧本；制作台父子页面共用，完整项目详情请求由两次变为零，元数据一次。
- 批次通过窄字段关联查询批量读取状态，由 MySQL 汇总告警。85 个成功任务的业务 SQL 从 172 次降到 3 次，不包含鉴权；H2 测试使用一次批量回退。
- 页面分区加载，概要或批次失败不阻塞编辑。模型目录和配置独立返回，自动选择等待两者到齐，显式选择不会被慢配置覆盖。
- 首屏只挂载 2 个重编辑器，接近视口时加载其他卡片，保留 20 个跳转锚点和页面级草稿。
- 分镜按稳定 episodeId 归属，重排后返回规范集数；空 ID 的历史数据仅在当前脚本/集数回退。视频编辑链接按 storyboardId 解析当前正确剧集和页码。
- 后端启用 HTTP JSON 压缩，未改变数据库协议压缩和连接池。

## 首次隔离阶段的 5 秒验收（历史）

本地生产前端构建 + 真实 Spring Boot HTTP + 专用 H2 内存数据，应用查询缓存关闭。浏览器为 Codex 内置 Chromium，1280×720；无网络/CPU 人工限速，单会话。

合成数据：85 集、851 条分镜，第一集 33 条，每页 20 条；每个提示词编辑器 32 个素材引用节点，并使用本地合成图片。

从导航开始，到原文框和提示词编辑器可编辑，且项目、模型、配置、素材、分页请求完成，再等待一帧。包含认证、资源解析、网络和渲染；不含输入登录凭证、后端启动或 AI 生成耗时。数据库缓冲未逐轮清空，不是物理冷读。

| 浏览器资源缓存 | 样本数 | P50 | P95 | 最大 |
| --- | ---: | ---: | ---: | ---: |
| 禁用 | 10 | 1.12 s | 1.24 s | 1.24 s |
| 启用 | 10 | 1.25 s | 1.37 s | 1.37 s |
| 合计 | 20 | 1.22 s | 1.30 s | 1.37 s |

每轮确认 2 个编辑器、20 个定位槽、32 个引用节点、1 次元数据请求和 0 次完整详情请求。前一阶段增强样本最大 2.32 秒，也均低于 5 秒；本表采用最终修正重建后的最后 20 次，不跨版本混算。

本地样本分页 JSON 正文约 249 KB，HTTP 压缩后约 4 KB。合成文本重复度高，不能推广其压缩比例。图片来自 localhost，未测实际 CDN 长尾下载；本轮没有真实模型生成调用。

## 真实 MySQL 只读对照

- 中间版本同一页查询结果由 845,601 字节降为 209,922 字节，减少约 75%；最后三次为 841.21 / 837.79 / 840.95 ms，含新连接和事务。
- 批次聚合返回 85 行、563 字节，三次为 499.28 / 467.74 / 463.32 ms；73 集有告警、合计 1,470 条，与之前统计一致。
- 更早对照有明显波动，包括精简查询约 11.5 秒，证据文件保留了这些数据；不能凭随后三次快查询承诺生产 P95。
- 上述 MySQL 对照在最终剧集重排兼容补丁之前。最终规范集数补丁经过 H2 SQL、控制器和前端回归；生产 HTTP 端到端 5 秒仍需合并、部署后验证。

## 验证记录

- 前端 7 个相关测试文件，122 项通过，覆盖引用、视频页兼容、视口挂载、草稿、慢配置、独立失败和定位。
- 后端一轮广覆盖 48 项通过，最后修正后另有 20 项通过；有重叠，不累加。最终 20 项包含 8 项编辑查询测试、真实鉴权控制器、项目元数据、读取边界和批次。
- 新回归先失败后通过：旧脚本/退休剧集混入、错误页码链接、剧集重排身份、慢模型配置覆盖显式选择。
- TypeScript、Biome 通过；Biome 有 3 条已有 cookie 测试警告。
- 前端生产构建、后端 Maven package 通过；不是全仓库所有测试全量通过声明。
- 浏览器确认保存内容跨页/跨集保留，错页码目标自动校正，目标卡片能延迟挂载；未观察到 error 日志。保存刷新期间需待当前刷新完成再操作分页。

关键日志在隔离工作区 .temp：storyboard-frontend-final.log、storyboard-backend-final.log、storyboard-reorder-green.log、storyboard-frontend-lint-final.log、storyboard-frontend-build-final.log、storyboard-backend-package-final.log。

原始毫秒样本、每个任务文件 SHA256 和限制见 outputs/storyboard-five-second-verification-20261021.json。不包含生产正文或认证凭据。

## 本地复现

测试入口为 backend/src/test/java/com/antshorttv/script/StoryboardBenchmarkServer.java，强制专用 H2 和 loopback，并提供合成账号数据。仅存在于测试类路径，不属于生产打包内容。账号信息定义在此类中。

在 backend 下运行 Maven test-compile 和 dependency:build-classpath，将 includeScope 设为 test、mdep.outputFile 设为 target/storyboard-test-classpath.txt。使用 target/test-classes、target/classes 和该依赖类路径运行 com.antshorttv.script.StoryboardBenchmarkServer。必须以 backend 为工作目录，且不要与另一 Maven 编译共用运行中的输出目录。

在工作区根目录运行前端生产构建，再运行 node scripts/storyboard-benchmark-preview.mjs。测试页面为 http://127.0.0.1:18190/user/login。性能测试分别禁用和启用浏览器资源缓存；专用内存数据测试结束后消失。

## 合并前识别的并行修改（现已定向合并）

以下 7 个文件在集成预检时已偏离开始时的摘要，因此没有整文件覆盖：

- backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java
- backend/src/main/resources/application.yml
- backend/src/test/java/com/antshorttv/script/ScriptWorkflowControllerTest.java
- frontend/src/pages/projects/production-workbench/index.tsx
- frontend/src/pages/projects/production-workbench/service.ts
- frontend/src/pages/projects/production-workbench/storyboard.tsx
- frontend/src/pages/projects/production-workbench/storyboard.test.tsx

以上为首次交付时的并行文件记录。本轮已完成主目录兼容合并，新增导航响应、资产归一化及媒体历史均已保留。
