# 剧集管理与小程序看剧交付说明

## 本次范围

管理端继续使用“剧集管理”入口，补充竖版封面、分类、连载/完结、计划总集数与已导入/已上架数量；支持视频预览、标题与首帧封面编辑、媒体信息重新检测、批量本地导入。上传来源仍使用已校验上传会话或有下载权限的项目成片。

发布先预检：校验小程序状态、剧集资料、集号、视频对象、时长和尺寸；显示新增/移除/变更集号与元数据差异。跳集允许发布但会提示。每次正式确认生成独立内容版本（release.id），保留历史；编辑草稿不会立即修改线上。排序值越小越靠前，同值时推荐优先，再按新版本排序。每个小程序单独保存排序与推荐，续发新集保留原设置。

新增原生源码目录 `miniapp/`：首页、搜索、详情、观看历史与全屏上下滑动播放器。导入与真机配置见 `miniapp/README.md`。

## 数据与升级

新增 Flyway `V177__miniapp_watching_experience.sql`，扩展剧集、单集与发布快照字段，新增观看进度表。

已有剧目无需重导视频。请在后台补充封面、分类、计划总集数，再对缺少时长/尺寸的单集点击“检测媒体信息”，预检通过后重新发布。历史已发布快照保留旧字段，直到重新发布。媒体分析复用现有腾讯云 COS CI `generateMediainfo`，必须具有对应权限和可识别的视频文件。

本地上传以团队+剧集+上传会话哈希识别重试；同一会话、相同集号/标题返回已有单集，避免弱网响应丢失后的重复复制。不同上传占用相同集号仍然报错。内部私有存储键不返回客户端。

## 新接口

接口响应统一为 `{success,data,errorCode,errorMessage}`。管理端沿用平台管理员与团队权限；小程序沿用独立 Bearer 会话，不能使用后台 Cookie。

| 接口 | 用途 |
| --- | --- |
| `PUT /api/drama-library/{id}/episodes/{episodeId}` | `{title,posterUrl}` 修改草稿 |
| `GET /api/drama-library/{id}/episodes/{episodeId}/preview` | 管理端300秒签名预览 |
| `POST /api/drama-library/{id}/episodes/{episodeId}/inspect` | 读取视频真实时长、宽高 |
| `POST /api/team-miniapps/{id}/dramas/{dramaId}/preflight` | `{episodeIds,sortOrder?,recommended?}` 发布预检；最终 PUT 发布会再次验证 |
| `GET /api/miniapp/catalog` | `page`、`pageSize`（最多50）、`keyword`、`category`；返回 items/total/categories/version |
| `GET /api/miniapp/dramas/{id}` | 当前已发布剧集与实际排序单集目录 |
| `GET /api/miniapp/history` | 当前 openid、当前小程序的最近100部仍可观看内容 |
| `PUT /api/miniapp/history/{dramaId}` | episodeId/positionSeconds/durationSeconds/completed/sequence/playbackSession |
| `DELETE /api/miniapp/history` | 清空当前用户在当前小程序的历史 |
| `POST /api/miniapp/playback-events` | dramaId/episodeId/event/elapsedMs；事件 FIRST_FRAME、ERROR、STALL |

原 `/api/miniapp/dramas`、单集目录和播放接口继续保留。新目录版本由已发布 release ID 集合计算，发布和下架均会改变；分页期间版本变化时小程序重载，避免排序变更造成漏项或重复项。

进度以服务端快照时长限制，不能使用上报的虚假时长越界保存。同一个播放会话只接受递增序号；并发首次写入使用唯一键及行锁。跨设备并发观看以服务端接收的最后更新为准。签名地址仍为300秒，小程序主动在到期前30秒刷新、保持播放位置，后台恢复时再次检查；不会持久化播放 URL。

基础质量指标使用 Micrometer：`miniapp.playback.events` 计数，`miniapp.playback.elapsed` 耗时，标签 `miniapp` 和 `event`。没有额外创建用户行为明细表。指标出口沿用现有服务端监控配置。

## 发布步骤

1. 后端执行迁移并部署，检查 COS 媒体分析、播放 CDN 与微信授权配置。
2. 管理端补资料和检测视频；选择目标小程序与单集，查看预检差异并确认上架。
3. 微信开发者工具导入 `miniapp/`，填写真实 AppID、HTTPS 接口域名并配置微信合法域名，完成真实视频联调。
4. 上传体验版；多小程序场景先将本次源码加入第三方模板库，再沿用管理端模板上传和体验码流程。
5. 在微信公众平台提交审核，通过后手动发布正式版。系统内容上架与微信代码发布是两个操作。

服务部署与微信代码发布的状态以 `docs/drama-miniapp-release-2026-11-12.md` 的发布记录为准。真机触摸、原生 video 层级、横版字幕与长暂停恢复，需要使用实际小程序配置完成验收。

## 本地验证记录（2026-11-12）

- 管理端：8个文件、99项相关测试通过；TypeScript、Biome及Ant Design检查通过，生产构建成功。全项目原有Biome警告及requestErrorConfig.ts的9条Ant Design usage提示仍保留。
- 后端：drama包13个测试类共111项，以及商业订阅和项目读取4个测试类39项测试通过。两项旧权限断言按当前MENU:DRAMA_LIBRARY授权模型更新，并增加新管理接口的非管理员拒绝用例。
- 小程序：16项Node行为测试、5页面/16个JavaScript/7个WXML静态检查及Biome通过。
- 已完成独立需求审查和代码复查；微信开发者工具原生预览与 1.1.0 源码上传成功，真机验收尚未完成。CDN 微信 Referer 视频 Range 联调返回 206；具体发布状态见发布记录。
