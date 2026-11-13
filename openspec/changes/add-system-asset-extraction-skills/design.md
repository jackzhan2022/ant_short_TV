## Context

当前资产 Agent 绑定基础与识别两个 Skill。分镜有租户覆盖和项目版本选择，但资产并发入口冻结配置时遗漏租户/项目。角色目录从固定 Markdown 解析视觉设定，资产提取仅保存 prompt，生图参数由弹窗独立填写。

## Goals / Non-Goals

**Goals:** 提供动态系统方案选择，保留默认效果，新增仿真人方案，完整贯通提示词与图片参数；任务重试保持同一规则。

**Non-Goals:** 本次不开放任意租户资产 Skill 编辑，不发起生产数据重提取或付费生图，不改变全剧归一策略。

## Decisions

1. 系统 Skill frontmatter 使用 purpose: asset-extraction 声明可选方案；目录实时读取文件，项目保存 asset_extraction_skill_code，空值解析为旧框架。固定基础 Skill 与唯一选中方案组合，排除其他资产方案。
2. 仿真人方案声明 prompt-contract: complete，默认沿用 legacy。冻结 Skill 正文和契约派生可信状态，保存不从当前项目重新推断规则。
3. generationSpec 保存在资产及形态 content_json 中，含 negativePrompt、aspectRatio、promptMode、skillCode、skillRevision；visualIdentity、artSupplement 独立保存。单次生成修改 prompt 时仍继承已存规格，用户明确覆盖参数优先。完整方案不再被项目画风追加污染。
4. 扩展保存 Schema 的可选字段和 sceneVariants，兼容旧 payload。完整契约对本次需要写入的提示词验证负向词、比例与完整模式，保留 FILL_EMPTY 既有提示词与配套规格。
5. 各入口传团队与项目并持久化冻结计划；重试恢复同一计划。防重含实际 Skill 内容修订，积分估算按项目解析。已有方案切换只影响后续任务。
6. 角色视觉设定优先读结构化 visualIdentity，历史 Markdown 保留回退。生图缺乏原生负向词支持时由供应商适配层明确处理。

## Risks / Trade-offs

- 出图品质无法仅靠字段验证保证 → 提供固定样本的人工出图验收清单；本次自动验证最终请求而不消耗付费额度。
- 新格式造成历史兼容回归 → 保留默认格式与旧 payload，新增覆盖保存/生图/选择与冻结的回归测试。
- 文件 Skill 被项目引用 → 删除受引用保护，缺失选择应明确报错，不能静默降级。

## Migration Plan

新增 project 可空方案标识，不修改既有资产正文。先部署新 Skill 与前后端，空选择使用默认。回退前停止新方案任务，保留已生成规格与快照。

## Open Questions

无。
