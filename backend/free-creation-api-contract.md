# 自由创作 API 契约

所有端点对已登录的有效团队成员开放，要求 X-Tenant-Id。响应沿用 ApiResponse。

- GET /api/free-creations/models：返回 {models,providers}，仅包含可用的图片／视频模型、生成参数和供应商标识，不返回供应商凭据或管理配置。

- GET /api/free-creations?scope=mine|team&mediaType=IMAGE|VIDEO&status=QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELED&keyword=&current=1&pageSize=20：返回 {records,total,current,pageSize}。keyword 同时匹配提示词和作者名。
- GET /api/free-creations/{id}：返回单条记录。
- POST /api/free-creations：{requestId,mediaType,modelId,prompt,parameters,references:[{kind,uploadSessionToken?,referenceId?}]}，两种引用来源二选一；requestId 按团队+作者唯一，相同请求幂等，变更参数拒绝复用原 requestId。
- POST /api/tenants/{tenantId}/points/estimate：{operations:[{url:"/api/free-creations",data:创建请求}]}，复用现有 estimatedPoints/availablePoints/sufficient/lines 响应。
- GET /api/free-creations/{id}/outputs/{outputId}/download：返回 {url,fileName}。仅已成功发布的作品可下载。

记录：{id,executionId,mediaType,status,prompt,creatorId,creatorName,createdAt,modelId,modelName,parameters,references:[{id,referenceId,kind,fileName,thumbnailUrl,previewUrl}],outputs:[{id,referenceId,kind,fileName,thumbnailUrl,previewUrl,downloadUrl,durationSeconds}],errorMessage}。

参数：IMAGE count/aspectRatio/quality/outputFormat/size/negativePrompt；VIDEO durationSeconds/resolution/intelligentDuration/aspectRatio/generateAudio/watermark。OpenAI Responses 图片供应商每次只支持一张，count 必须为1；gpt-image-2质量支持 STANDARD（模型默认）、auto、low、medium、high，其他OpenAI图片模型可按现有适配器支持xhigh/max。视频依据既有 videoGeneration 约束验证；非Seedance仅支持一张首帧图片引用。

团队记录默认团队可见，无分享字段。上传引用必须为当前用户本团队无项目已完成会话，referenceId 可复用本团队已授权参考素材或已完成作品；拒绝跨团队或未发布作品。作品与封面保存到现有私有对象存储，预览和下载使用受权签名链接，不向客户端暴露供应商地址。

执行业务类型 FREE_CREATION，scene free_creation_image/free_creation_video，projectId为空。视频提交与轮询使用现有VIDEO_SUBMIT/VIDEO_QUERY执行池。已开始提交但无持久化响应/外部ID的崩溃记录进入计费待核对，禁止自动重发。已持久化供应商响应仅自动重试保存、封面和发布。

任务中心使用任务键 `FREE_CREATION:{id}`，列表、筛选、统计、详情、参考素材及结果均纳入 `/api/tenants/{tenantId}/production-tasks`。所有有效团队成员均可查看自己的自由创作任务及统计、访问本团队创作详情；团队范围沿用任务中心的团队管理员权限。结果仅在成功后签发媒体链接，图片、视频和音频参考素材分别展示。业务跳转为 `/free-creation?creation={id}`。

记录额外返回 `taskKey` 和 `allowedActions`。控制限当前团队内有效成员中的原提交人：PENDING/RUNNING 可 CANCEL，FAILED/TIMED_OUT 可 RETRY，SUCCEEDED/CANCELED 可 REGENERATE。自由创作页面和任务中心均调用 `POST /api/tenants/{tenantId}/production-tasks/FREE_CREATION:{id}/{cancel|retry|regenerate}`，返回操作后的任务摘要；重试和重新生成返回新任务键，客户端应切换详情。

RETRY/REGENERATE 按原模型、提示词、参数及已授权参考素材创建新业务记录和新执行，重新校验模型并预占积分，原任务与原结果保留。请求须携带 `Idempotency-Key`（非空，最长128字符），按团队、用户、源记录和操作隔离；相同键重复提交只创建、预占一次。操作前通过现有积分预估端点传入对应控制 URL。取消可重复调用，撤销本地执行权；供应商已受理的调用不能保证远端停止，未调用积分释放及已调用用量由现有终态对账处理（默认60秒周期），未知用量保留待核对。

通用 `/api/tenants/{tenantId}/ai-executions/{executionId}/{cancel|retry|regenerate}` 对自由创作转交同一控制逻辑，校验有效团队成员及提交人身份。retry 使用 `Idempotency-Key` 请求头，regenerate 沿用请求体 `clientIdempotencyKey`；返回新执行 ID 和对应 `businessId`。同一源任务和相同操作标识在两个入口间共享幂等结果。
