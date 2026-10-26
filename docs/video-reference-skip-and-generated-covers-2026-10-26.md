# 分镜缺图跳过与成片封面

## 使用行为

1. 引用图片缺失时，生成确认按钮改为“跳过缺图继续生成”。只跳过本次不可用图片，其余有效图片、视频及音频仍按模型约束参与生成。
2. 缺图引用在本次提示词中还原为名称文字，剩余图片重新编号；原提示词文档和资产绑定不改动。没有任何可用参考素材时才按文字生成。
3. 积分估算和正式创建使用相同跳图规则。重新生成会重新检查图片可用性；显式纯文生成保持兼容。
4. 视频完成后，从存储的成片截取第 1 秒画面；短视频或该帧不可用时尝试第 0 秒。保存 JPEG 原图，并提交现有图片展示版本处理流程。
5. 成片封面作为 VIDEO_COVER 图片结果关联视频任务，收入“项目资产库 → 图片 → 其他”，来源显示“视频截帧”，名称包含剧集、镜头和视频封面标记。
6. 主预览和视频版本缩略图均使用对应结果的 coverUrl。展示版本尚未处理完成时，封面接口使用已登记的截帧 JPEG 原图；展示版本完成后使用展示图。
7. 封面重试复用同一图片身份和已登记原图。封面图片和视频结果在同一短事务发布，截帧、下载及上传不占用视频发布事务。
8. 已被视频或素材引用的封面不能普通删除；强制删除在同一事务清理关联封面地址。

## 代码位置

- `frontend/src/pages/projects/production-workbench/storyboard.tsx`：缺图确认、提交参数与省略提示。
- `backend/src/main/java/com/antshorttv/video/AvailableVideoReferences.java`：图片可用性检查、文字还原和引用重新编排。
- `backend/src/main/java/com/antshorttv/video/AiVideoCoverService.java`：自动截帧、原图与展示版本存储、重试复用及图片资产发布。
- `backend/src/main/java/com/antshorttv/video/AiVideoTaskService.java`：估算/创建、重生成和成片发布集成。
- `backend/src/main/java/com/antshorttv/material/ProjectAssetLibraryService.java`：封面资产列表与项目隔离。
- `backend/src/main/java/com/antshorttv/material/MediaCoverDeliveryService.java`：视频封面展示及异步处理期间的原图交付。
- `backend/src/main/java/com/antshorttv/aiimage/AiImageTaskService.java`：封面引用的删除保护。

## 验证

回归覆盖：混合缺图、全部缺图、取消生成、批量估算与提交一致、图片重编号、原绑定保留、补齐图片后重新生成、截帧失败后的身份复用、封面资产查询与隔离、展示图未就绪时原图交付、封面主预览/版本缩略图一致、引用删除保护及强制解绑。

验证结果：前端 133 项相关测试通过；后端本轮 48 项专项回归通过（此前已完整运行 28 项视频控制器回归）；前端 lint、TypeScript、antd lint 和生产构建通过。

验证日志保存在 `outputs/video-references-covers-*20261026*` 和 `outputs/video-cover-deletion-red-20261026.log`。

## 范围

本次完成本地实现与验证，未执行生产部署。自动封面作用于新完成的视频，既有视频未批量回填。截帧依赖现有腾讯云 COS 数据万象服务；离线测试通过存储与截帧服务桩验证行为，未调用生产云资源。
