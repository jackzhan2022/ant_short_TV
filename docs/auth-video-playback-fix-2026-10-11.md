# 登录和注册页背景视频黑屏修复

## 原因与改动

原资源 `https://zy-dimnx.oss-cn-shenzhen.aliyuncs.com/posters/loginVideo.mp4` 返回 200，但视频轨为 HEVC Main、1080×1200，另有 AAC 音轨，总大小 76,226,554 字节。线上浏览器出现 `readyState=4`、`paused=false`、播放时间推进但 `videoWidth=videoHeight=0` 的状态。原逻辑仅凭 loadeddata/canplay/playing 事件隐藏备用图，导致无法解码 HEVC 画面的浏览器显示黑屏。

修复复用原视频内容，使用 FFmpeg 转成 H.264 / yuv420p、720×800、24 fps，移除静音背景不需要的音轨并启用 faststart。完整视频约 27.58 秒，大小 7,044,271 字节，随前端发布至 `/media/auth-background-h264.mp4`。只有存在当前帧且视频宽高都大于 0 时才显示视频；保留缓存视频就绪处理和错误时显示备用图的行为。

## 验证

- 新增无可解码视频轨时保持备用图的回归测试，修复前失败、修复后通过。
- 登录、注册和布局共 14 项测试通过。
- `npm run lint` 和 `npx antd lint ./src` 退出码均为 0；保留已有的 3 条 cookie 测试警告及 11 条 antd 警告，无本次新增警告。
- 前端生产构建通过。
- 线上新视频返回 200 / video/mp4，大小与本地产物一致。
- 刷新线上注册页后实际看到视频画面；浏览器报告 720×800、readyState=4、paused=false、currentTime≈8.93、error=null。

## 发布与回滚

修复工作区：`C:/Users/12775/.codex/worktrees/auth-video-playback/ant_short_TV`，基于线上提交 `f4c82a59a27462cfa35379c6108fc0ffa002d1b6`。主工作区也同步了相应源码、测试和视频文件。

- 原发布目录：`/opt/antv/releases/20261009152236-f4c82a5-ui-improvements`。
- 新发布目录：`/opt/antv/releases/20261011-auth-video-f4c82a5`。
- 仅更新前端；新发布目录的 backend 链接指向原发布 backend，后端二进制、配置与进程保持不变。
- 保留旧静态资源块，避免已打开页面加载旧资源时出现 404；以原子符号链接切换 `/opt/antv/current`。
- 发布包 SHA-256：`5780be769d960e704582f98a9e1e3d23e2d56a0c9f0413d8a15e3377511f9d47`。
- 视频 SHA-256：`cb4f9cefa8b9d1dc574557fc908522f7c69d1cbbda962cca90d5ec848005f8b5`。

如需撤回这次纯前端发布，将 current 原子切回上述原发布目录即可；不需要重启后端或恢复数据库。
