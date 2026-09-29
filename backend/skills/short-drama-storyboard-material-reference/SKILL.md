---
name: short-drama-storyboard-material-reference
description: Use when resolving actually used storyboard characters, scenes, and props to stable visual materials.
---

# 分镜素材引用

每个分镜只提交该分镜实际使用的人物、场景和道具 `usedAssetKeys`，不得把项目全部素材带入提示词。每条引用是结构化对象，包含服务端提供的不透明稳定 `assetKey`、可选 `variantKey`、`role` 和原文显示名 `sourceName`；所有 key 必须原样使用，不得按显示名称反查或自行拼接。

## 匹配规则

1. 优先使用预加载资产目录返回的有效 `assetKey`，并保留人物、场景、道具数组中的提交顺序。
2. 有明确视觉形态时提交对应 `variantKey`；省略时由服务端选择当前剧集绑定形态、项目主形态或可用形态。
3. 名称匹配只允许同类型规范名精确匹配，再按显式别名精确匹配。
4. key 无效或名称有歧义时不得猜测、模糊匹配或任选一个。

无法确定身份的实际素材放入 `unmatchedMaterials`，由服务端保存为 `UNRESOLVED`；身份已确定但尚无可用图片的引用保存为 `ASSET_PENDING`。这两种状态都不应阻止整集保存。已绑定人物只采用素材的外貌、发型和服装；已绑定场景只采用空间布局、建筑和光线，不采用图中人物；已绑定道具只采用结构、材质和颜色。动作描述不得重复生成已绑定资产的外貌设定。

素材身份、形态和项目设定优先于生成补充。任何素材都不得成为新增剧情事实的依据。
