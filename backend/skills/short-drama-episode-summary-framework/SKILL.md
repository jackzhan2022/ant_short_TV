---
name: short-drama-episode-summary-framework
description: Use when producing the formal editable summary document for one current short-drama episode.
---

# 剧集概要提炼框架

本 Skill 只总结 `read_current_episode` 返回的当前剧集。不得依赖其他剧集、旧概要、全局理解或调用方提示中的剧情事实；不得输出角色、角色变装、场景、道具等资产产物。

## 正式字段

提交给 `save_episode_summary` 的对象只能包含：

- `summary`：非空的精炼叙事，按当前剧集的时间顺序说明关键行动、信息揭示、冲突变化和结果。只保留原文明示的因果；先后发生不等于因果，不得编造动机、幕后人物或事件原因。
- `highlights`：2 至 5 条非空字符串。每条对应一个不同的关键事件、决定、揭示或反转，按发生顺序排列。不得用不同措辞重复同一事实，不得写“冲突升级”等泛化评价，也不得变成人物、场景、服装或道具清单。
- `endingHook`：字符串或 JSON `null`。只有结尾明确存在未解决威胁、新信息、反转、承诺或待回答问题时才写，并复述其直接证据。普通离场、和解、获胜、结案或镜头结束不是钩子；没有证据时必须为 `null`，不得写空字符串、“无”“暂无”或自行制造下一集悬念。

## 事实边界

所有字段只能引用当前剧集正文中出现或由其直接支持的事实。角色名、地点和物件名称保持原文一致。不要把未知脚步认定为某个人，不要用全局理解补齐钥匙用途，也不要为使概要“完整”而引入上一集或下一集内容。

## 保存前检查

确认顶层只有 `summary`、`highlights`、`endingHook`；summary 时间顺序正确且没有无证据因果；highlights 数量在 2 至 5 且语义不重复；endingHook 的分类和措辞有结尾证据；无其他剧集、全局理解、旧结果、资产字段或业务标识。

检查通过后调用 `save_episode_summary`。失败时按工具错误修正后重新提交；只有保存成功才算完成，成功后不得再次保存或继续生成。
