# 画风 2.0 生成示例图

11 种画风各一张，均于 2026-11-04 通过剧智创「自由创作」上传旧版参考图后生成，采用新人物、新场景和新构图。
10 张使用 gpt-image-2.5-sunburst（high）；国风赛博三维在两次服务不可用后改用 gpt-image-2.5-flare（medium）生成。
统一约 16:9，每种保留一张成功结果。1672×941 的原始 PNG 上传 COS，经腾讯云 CI imageSlim 生成展示图。
网页读取 `/api/style-library/v2/images/{code}`，后端检查展示图 READY 后返回 CDN 签名地址；不再随前端打包本地 WebP。
逐图生成任务、模型、提示词、图片校验值及旧参考图来源见 sources.json；不保存有时效的签名链接。

参考图来源：HBAI-Ltd/Toonflow-app，提交 cd3e7c4e83963bea255be2e621eb78d2cd1c2188。
原参考图适用该旧版的 Apache 2.0 与补充商业授权条款，不是文字知识库版本的 MIT 许可。
保留 LICENSE-Toonflow.txt 和 NOTICES-Toonflow.txt；参考生图不自动消除原参考素材的权利义务。

11 张图已完成对象存储导入、压缩回调与 CDN 访问验证。原图及旧 WebP 备份保存在本地 outputs 目录。
页面与接口已于 2026-11-04 随版本上线。本次图片链路调整不改变资产提示词规则、分镜技能或模型配置。
