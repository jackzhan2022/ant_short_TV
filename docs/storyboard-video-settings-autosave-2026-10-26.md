# 分镜视频生成参数自动保存

## 使用行为

- 时长、分辨率、生成音频、水印修改后，自动保存到对应分镜。
- 刷新或重新进入时恢复已保存参数。没有保存过的分镜继续使用分镜/项目默认值。
- 参数区显示“正在保存视频参数”“视频参数已保存”，失败时显示重试按钮，保留当前编辑内容。
- 单条和批量生成都会等待相应分镜最新参数保存成功；失败时不提交视频任务和积分估算。
- 连续修改按分镜串行保存，离开后返回会接续尚未完成的保存并恢复最新待保存值。

## 存储与读取

迁移 `V159__storyboard_video_generation_settings.sql` 新增 `storyboard.video_generation_settings_json`。

接口 `PUT /api/projects/{projectId}/storyboards/{storyboardId}/video-generation-settings` 保存四项完整配置，`GET` 读取。写入要求 `STORYBOARD:EDIT`，读取要求 `STORYBOARD:VIEW`，均校验租户、项目、分镜和删除状态。

分镜实体响应、聚焦编辑页、工作台分页响应均返回 `videoGenerationSettings`。配置独立于剧本时长，普通剧本、提示词或视频绑定更新不改动该配置；实体字段设置 `updateStrategy=NEVER`，防止旧实体的通用更新覆盖新参数。

## 验证

前端 127 项相关测试、后端 19 项回归测试通过；lint、TypeScript、antd 检查及前端生产构建通过。

覆盖刷新恢复、分镜隔离、四字段快速连续修改、保存失败与重试、生成等待、等待期间追加修改、切换项目、离开再返回、旧实体更新保护，以及普通编辑保留参数。

日志和机器可读结果：`outputs/storyboard-video-settings-*20261026*`。

## 上线范围

本次完成本地实现与验证，尚未部署。上线需执行新增数据库迁移。保存范围为四项生成参数，不包含模型选择和历史视频任务参数回填。
