# ANTV生图

独立 Windows 桌面图片工具，交互参考项目的「自由创作」。无需启动原项目的 Java 后端、数据库或前端服务。最终用户无需安装 Node.js。

## 使用

1. 运行 `release-antv/ANTV生图-0.1.0-Setup.exe` 安装，安装程序会创建带剧智创品牌图标的桌面快捷方式。也可直接运行 `ANTV生图-0.1.0-Portable.exe`。
2. 打开右上角个人设置，填写自己的熊熊爱 API Key；服务地址固定为 `http://89.58.47.162:8080`，不能在界面修改。
3. 在个人设置中选择图片自动保存目录，默认是当前用户「图片」下的「ANTV生图」。目录选择立即生效，已有图片不迁移。
4. 填写提示词、图片模型、比例、质量和输出格式。可以上传、拖入或粘贴参考图。最多 8 张，每张 10 MB，总计 30 MB。
5. 在生成按钮上方填写自定义图片名称，不需要扩展名；留空时使用本地时间自动命名。重名会追加 `_2`、`_3` 等序号，绝不自动覆盖。
6. 点击生成或按 `Ctrl+Enter`。原图自动保存到选定目录，创作记录显示保存名称。
7. 创作记录和大图预览均可「添加为参考」，追加图片并保留当前提示词和其他参考图；相同图片不会重复添加。「复用参数」会经确认替换当前提示词、参数和参考图。「另存原图」可选择新的文件名和位置。

## 本机与远程的边界

- 窗口、参数校验、Key 加密、调用编排、历史记录、参考图片和文件保存全部在本机运行。
- 实际图片推理由熊熊爱远程服务完成。点击生成才会发送提示词、参考图及 Key。
- 调用协议沿用 `XiongXiongAiAdapter`：向固定地址的 `/responses` POST，外层默认 `gpt-6-astra`，`tools[].type=image_generation`，图片模型默认 `gpt-image-2`。带参考图时使用 `action=edit`，否则 `generate`。
- API Key 通过 Electron `safeStorage` 使用 Windows DPAPI 加密，按 Windows 用户保存于 `%APPDATA%/antv-image-studio/settings.json`；不回传给界面，不写入创作记录。程序不内置任何用户 Key。
- 服务地址由需求指定为 HTTP，因此本机加密保存不代表网络传输已加密。
- 原始图片、缩略图、提示词和参考素材另保存在应用用户目录的 `creations/`，确保移动默认导出目录后旧记录仍能打开。便携版也使用当前 Windows 用户目录保存个人配置。
- 图片请求最长等待 10 分钟，每次只提交一次，不自动重试。停止等待或退出程序不能保证上游停止计费；重启后未完成记录标记为中断。
- 默认目录暂时不可写时，原图仍保留在本机记录，显示明确提示并允许手动另存。
- 按 Windows 用户隔离配置；同一 Windows 用户内不提供额外的应用账号登录系统。

## 开发与验证

Node.js 22 或以上。包管理器为 npm。

```powershell
cd desktop-image-tool
npm ci
npm start
npm run lint
npm test
npm run smoke
npm run pack
```

- TypeScript 严格类型检查，Biome 格式与静态检查。
- 单元测试覆盖调用协议、错误与取消、密钥存储、创作记录恢复、目录配置和文件名冲突。
- Electron 集成检查使用隔离的临时用户目录及本地图片响应，验证实际窗口、Key 加密、参考图追加、预览、保存、自定义名称、重启恢复和停止等待，不产生外部生成费用。
- 品牌图标沿用项目 `frontend/public/juzhichuang-logo-mark.png`，副本在 `assets/juzhichuang-logo-mark.png`。执行 `python scripts/create-icon.py` 可用 Pillow 重建 PNG 与多尺寸 ICO，保留原图比例及透明背景；最终运行与打包无需 Python。
- `npm run pack` 生成 Windows x64 安装版与便携版，均嵌入 ICO。安装包未配置商业代码签名证书。

真实服务的模型权限、余额和生成结果需要用户配置有效 Key 后验证。公开接口无凭据访问已确认返回 401；测试不会读取项目原有用户 Key。
