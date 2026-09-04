---
name: short-drama-storyboard-material-reference
description: Use when resolving actually used storyboard characters, scenes, and props to stable visual materials.
---

# 分镜素材引用

每个分镜只提交该分镜实际使用的人物、场景和道具 `usedAssetKeys`，不得把项目全部素材带入提示词。资产 key 是服务端提供的不透明稳定标识，必须原样使用。

## 匹配规则

1. 优先使用 `read_script_assets` 返回的有效 `assetKey`。
2. 同一资产的视觉形态优先使用当前剧集绑定形态，其次使用项目主形态。
3. 名称匹配只允许同类型规范名精确匹配，再按显式别名精确匹配。
4. key 无效或名称有歧义时不得猜测、模糊匹配或任选一个。

未匹配名称保留为普通文本，最终分镜会标记为 `ASSET_PENDING`。已绑定人物只采用素材的外貌、发型和服装；已绑定场景只采用空间布局、建筑和光线，不采用图中人物；已绑定道具只采用结构、材质和颜色。动作描述不得重复生成已绑定资产的外貌设定。

素材身份、形态和项目设定优先于生成补充。任何素材都不得成为新增剧情事实的依据。
