# 自由创作 API 契约

所有端点要求平台管理员、有效团队成员和 X-Tenant-Id。响应沿用 ApiResponse。

- GET /api/free-creations?scope=mine|team&mediaType=IMAGE|VIDEO&status=QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELED&keyword=&current=1&pageSize=20：返回 {records,total,current,pageSize}。keyword 同时匹配提示词和作者名。
- GET /api/free-creations/{id}：返回单条记录。
- POST /api/free-creations：{requestId,mediaType,modelId,prompt,parameters,references:[{kind,uploadSessionToken?,referenceId?}]}，两种引用来源二选一；requestId 按团队+作者唯一，相同请求幂等，变更参数拒绝复用原 requestId。
- POST /api/tenants/{tenantId}/points/estimate：{operations:[{url:"/api/free-creations",data:创建请求}]}，复用现有 estimatedPoints/availablePoints/sufficient/lines 响应。
- GET /api/free-creations/{id}/outputs/{outputId}/download：返回 {url,fileName}。仅已成功发布的作品可下载。

记录：{id,executionId,mediaType,status,prompt,creatorId,creatorName,createdAt,modelId,modelName,parameters,references:[{id,referenceId,kind,fileName,thumbnailUrl,previewUrl}],outputs:[{id,referenceId,kind,fileName,thumbnailUrl,previewUrl,downloadUrl,durationSeconds}],errorMessage}。

参数：IMAGE count/aspectRatio/quality/outputFormat/size/negativePrompt；VIDEO durationSeconds/resolution/intelligentDuration/aspectRatio/generateAudio/watermark。OpenAI Responses 图片供应商每次只支持一张，count 必须为1；gpt-image-2质量支持 STANDARD（模型默认）、auto、low、medium、high，其他OpenAI图片模型可按现有适配器支持xhigh/max。视频依据既有 videoGeneration 约束验证；非Seedance仅支持一张首帧图片引用。

团队记录默认团队可见，无分享字段。上传引用必须为当前用户本团队无项目已完成会话，referenceId 可复用本团队已授权参考素材或已完成作品；拒绝跨团队或未发布作品。作品与封面保存到现有私有对象存储，预览和下载使用受权签名链接，不向客户端暴露供应商地址。

执行业务类型 FREE_CREATION，scene free_creation_image/free_creation_video，projectId为空。通用执行查看另加管理员校验；通用 retry/regenerate/cancel 禁用此业务，重新生成使用新POST。视频提交与轮询使用现有VIDEO_SUBMIT/VIDEO_QUERY执行池。已开始提交但无持久化响应/外部ID的崩溃记录进入计费待核对，禁止盲目重发。已持久化供应商响应仅重试保存、封面和发布。
