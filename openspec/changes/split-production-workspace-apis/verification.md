# 验证记录（2026-09-11）

## 后端读取链优化（待本次提交后的发布验收）

- 轻量分集导航、分析阶段的三类批量读取和访问上下文复用已实现；完整范围及未完成项见 `handoff.md`。
- 提交前重新执行 `mvn -f backend/pom.xml '-Dtest=ScriptWorkflowReadBoundaryTest,ScriptAnalysisReadBoundaryTest,ScopedPermissionGuardTest,ScriptWorkflowControllerTest,ScriptEpisodeServiceTest' test`：32 项通过，0 failures，0 errors，0 skipped（2026-09-11 19:07）。
- 随后执行 `mvn -f backend/pom.xml -DskipTests package`：退出码 0，生成 Spring Boot JAR（2026-09-11 19:07）。
- 认证态项目 33 性能对比不在本地测试范围内，仍保持任务 7.2 未完成。

## 线上部署（15:49）

- 已确认 GitHub master 为 `c536c33618a0e0eb5a6fc19a7b00b71bb96afcf0`，发布目录 `/opt/antv/releases/202609111542-c536c33` 已切换生效。
- 数据库与 Skill 备份位于 `/opt/antv/backups/202609111542-c536c33`，gzip 与 tar 完整性检查通过。旧版本 `/opt/antv/releases/202609111120-8a2d823` 保留用于回滚。
- 发布前重跑前端 300 项测试、lint/type checks、Ant Design lint 及前后端构建，退出码均为 0；既有警告保留。前后端上传包 SHA-256 与本地一致。
- 服务状态 `active/running`，`NRestarts=0`；公网首页 HTTP 200，公网首页与服务器及本地 index.html SHA-256 一致。
- 未认证访问 currentUser、script-workspace、script-page-workspace、asset-settings-summary、带分页参数的 storyboard-workspace 和 script-content 均返回 401。这仅验证服务及认证拦截，不证明认证后的业务响应正确或性能达标。
- 7.2 继续保持未勾选：本轮浏览器桥接仍报 `nodeRepl.fetch request failed`，未取得项目 33 的认证态耗时、响应大小及 SQL 查询数。最新版本未部署这一前置阻碍现已解除。

## 后续实现与复验（15:29 更新，取代下文的早期缺口结论）

- 当前任务清单为 **23/24 完成**，仅 7.2 保持未勾选；下文早期审计为历史记录，不代表当前状态。
- 新增历史版本选择、按选择加载正文、失败重试与请求过期保护，新增用例通过。
- 补充轮询完成/失败后停止且仅协调刷新一次的定时器测试，通过。
- 设定页视觉变更（含新增）现在刷新摘要与活动资产详情；补充请求数量断言和详情失败重试用例，通过。
- 分镜页切集/翻页/保存现在只重读当前分镜页，不再重读剧本及资产摘要；媒体接口失败保留分镜、保存刷新边界用例通过。
- 新增 AssetSummaryQueryBoundaryTest：实际执行每类 50 个资产的行映射，验证仅 3 次 JDBC 查询，视觉变体、绑定、解析、候选服务均无交互。与 EpisodeSplitWarningsTest、修正后的 EpisodeSplittingToolSchemaTest 合计 8 项通过。
- 审核页高亮问题是 selectedIssue 对象引用变化重置高亮，改为依赖实际标识和首条摘录；原失败测试及该页 20 项通过。
- 后端 schema 已明确支持 v1 文本边界与 v2 片段 ID，原测试仍只允许 v1 字段；测试更新为支持两种字段并继续拒绝全文/资源 ID，定向复验通过。后端验证口径为此前全量 929 项运行 + 失败项修正复验 + 新增测试，并非重新跑完整套件。
- 最新前端全量 68 文件、300/300 通过。npm run lint 通过（6 条既有警告）；Ant Design lint 14 条既有告警，无新增组件 API 变更。
- 最终前端生产构建退出码为 0，Webpack 编译完成（30.12 秒）；保留 Browserslist 数据过期提示。本轮补充改动尚未部署线上。
- 7.2 仍未完成：浏览器桥接重置后仍不可用；未编造项目 33 的认证态性能或 SQL 查询数。需要可访问的认证环境并确认新版本部署后才能采样。

本记录区分测试执行完成与验收通过，不将缺失的实现或测试标为完成。

## 早期执行结果（历史记录，修正结果见上文）

- 制作台前端专项：15:03 重新运行 script、index、settings、storyboard、ShotProductionWorkspace 五个测试文件，43/43 通过。
- 后端全量：14:56:53 已结束，耗时 37:22，929 tests run，1 failure，0 errors，1 skipped（927 通过）。日志：`C:/Users/jiafeng.zhan/AppData/Local/Temp/codex-backend-full-test.log`。
- 后端失败：`EpisodeSplittingToolSchemaTest.splitSaveAcceptsOnlyOrderedTitlesAndSourceMarkers:27`，预期必填字段 title、startMarker、endMarker，实际为 title。不能据此宣称全量通过；该失败的原因尚未完成诊断。
- 前端全量：294 通过、1 失败（295 总计）。失败为 `ScriptReviewPage > switches the highlighted evidence when choosing another hit`，预期周野的命中内容，实际仍为林晚。日志：`C:/Users/jiafeng.zhan/AppData/Local/Temp/codex-frontend-full-test.log`。
- 本轮优化此前已通过后端定向集成 3 项、TypeScript、后端编译及前端生产构建；Biome 有 6 条警告，Ant Design lint 有 14 条告警，不能称为零告警验收。
- 本次重置浏览器 JS 会话后重新运行 `cua.getState()`，依旧返回空浏览器列表与 `nodeRepl.fetch request failed`，尚未获得认证态测量结果。

## 早期未勾选任务审计（历史记录）

| 任务 | 验证结果与剩余条件 |
| --- | --- |
| 1.3 | 现有旧聚合响应/分集警告兼容用例存在；未找到资产摘要不展开视觉工作区的服务层查询边界断言。 |
| 3.2 | 五个制作台测试文件通过；仍需完整的首屏请求排除断言。mock 聚合接口未导出并不等价于覆盖所有首屏请求边界。 |
| 4.1 | 剧本首屏已经迁移；script.tsx 未调用 queryScriptVersion，历史版本选择及全文按需加载未完成。当前原文弹窗不能替代历史版本验收。 |
| 4.3 | 现有失败状态、初始加载壳、重分析执行刷新测试通过；缺少定时轮询终止/单次协调刷新及历史版本按需加载的完整断言。此前“正文按需已有覆盖”的表述过宽：现有 mock 不能证明分集切换、缓存及失败恢复均正确。 |
| 5.3 | 设定页现有测试通过；缺少视觉详情失败和变更后仅刷新活动详情及摘要的专门断言。 |
| 6.4 | 分集切换、分页及过期响应测试通过；缺少可选媒体请求失败的验收用例，变更后当前分页刷新仍需精确断言。 |
| 7.1 | 全量测试执行已结束但前后端各有一项失败；保持未勾选。 |
| 7.2 | 未获得项目 33 认证态的耗时、查询数和响应体积对比；保持未勾选。优化版本此前尚未部署，线上旧版本采样也不能充当新版本验收。 |

当时任务状态为 16/24；后续功能、回归与失败项修正已记录在本文顶部，当前仅剩匹配版本的认证环境性能采样。
