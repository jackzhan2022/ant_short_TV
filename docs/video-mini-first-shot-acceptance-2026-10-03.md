# 第一镜 mini 视频生成验收

用户要求：项目 44，第 1 集第 1 镜（storyboard 1041），Seedance 2.0 mini，4 秒，480p，只测试一条视频。

## 发现与处理

1. 四条正式素材引用仍为 ASSET_PENDING，但形态 2921、2916、2917、2922 均已完成图片。通过分镜页原样重选已有默认形态保存，四条引用恢复 RESOLVED，没有更换图片或新增生成。
2. 视频任务 1（execution 178502，model 11）实际使用 4 秒、480p，但火山提交返回 HTTP 400：content[1].image_url 拉取超时。数据库快照确认模型引用使用 COS 内网域名。
3. 修复 modelAccessUrl：对公网 COS Host 签名，保留原始编码路径、查询签名、临时会话 token 和请求有效期；后台存储客户端继续使用内网。未修改桶权限或凭据。

## 代码验证

- 提交：d749227，开发分支 codex/video-model-public-url，已快进合并到本地 master。
- 真实 COS SDK 5.6.227 回归测试先复现内网域名错误，再验证公网域名、HTTPS、空格路径、临时 token、公网 Host 签名重算及有效期。
- 5 个相关测试类合计 34 项通过，Maven package 成功；独立代码复核通过。
- 前端未修改；根工作区相同版本前端的 npm run lint（含 tsc）和 npx antd lint ./src 通过，存在原有非阻断提示。
- JAR SHA-256：010a4b89b1b93d48e7becb8df8816c95d1fea9e8da78380d12cfd5b44eb82943。
- 发布目录：/opt/antv/releases/20261003-d749227-video-public-url。
- 回退版本：/opt/antv/releases/20261003-74f788e-image-style。

## 最终结果

- 上线健康检查通过；旧版本保留，无数据库结构变更，发布时无活动生成执行。
- 通过原失败任务的“重试”按钮创建任务 2，execution 178503，model 11（Seedance 2.0 mini），实际提交参数 4 秒、480p、16:9。
- 四张模型参考图均使用公网 COS 签名域名。火山接受任务 cgt-20261003013148-hzccg，随后生成成功。
- 结果 1 已保存并通过正常 UI 设为第 1 镜当前视频；浏览器刷新路由后仍能读取此结果。
- MP4 文件大小 1,425,400 字节，存储键 materials/1/44/videos/202610/2/result/original.mp4。
- 服务商/数据库元数据为 4.00 秒、853×480；**浏览器实际解码为 4.096 秒、864×496**。按用户要求提交了 4 秒、480p，但不能声称实际文件精确等于该规格；未额外裁剪或转码。
- 浏览器 HTMLVideoElement readyState=4、error=null、ended=true、currentTime=4.096，完整播放验证通过。
- 失败执行 178502：RELEASED，结算 0、释放 5 积分。成功执行 178503：SETTLED，结算 5 积分。
- 数据库去敏证据：.temp/video-mini/final-result.json。未下载媒体绕过浏览器限制。
