# 私域虚拟人像入库联调

当前功能：每张角色形态图一个持久化任务；一次提交最多 100 张，可跨角色。任务中心类型 VIRTUAL_AVATAR，支持查询详情、查看结果资产 ID、重试和返回角色工作台。普通图片不显示虚拟标签。

## 配置

后端进程需要 ARK_ASSET_ACCESS_KEY / ARK_ASSET_SECRET_KEY；这是火山控制面 AK/SK，不能用 VOLCENGINE_ARK 推理 API Key 替代。密钥存放于受保护的运行配置，不写前端，不通过聊天粘贴。本次本机配置文件为项目根目录 env（不是 backend/env），已由 Git 忽略；隔离联调进程从该文件读取。

ARK_ASSET_REGION 默认 cn-beijing；ARK_ASSET_PROJECT_NAME 默认 default；ARK_ASSET_ENDPOINT 默认 https://ark.cn-beijing.volcengineapi.com。素材库项目和 Seedance 推理资源需保持一致。更改这些值需要重启后端。env.example 只含空凭据示例，运行进程须通过现有启动方式加载实际环境变量。

火山账户需已开通对应素材库权益、签署控制台授权并有素材管理 IAM 权限。本接口为已有虚拟人像的素材登记与审核，不是真人图片转绘。

## 本地 API

使用正常登录会话、X-Tenant-Id 和现有 CSRF 规则调用：

1. GET /api/projects/{projectId}/virtual-avatar-tasks/candidates?page=1：每页 100 张；可传 variantId 只查单张，返回角色名、形态名、入库状态。
2. POST /api/projects/{projectId}/virtual-avatar-tasks，JSON `{"variantIds":[11,12,23]}`：返回每项的 id/taskKey；无效项返回 variantId/errorMessage，其他项继续提交。
3. GET /api/projects/{projectId}/virtual-avatar-tasks/{id}：查询本地状态；后台独立调度器自动执行 CreateAssetGroup → CreateAsset → GetAsset。
4. POST /api/projects/{projectId}/virtual-avatar-tasks/{id}/retry：仅任务创建者可重试可恢复的失败任务；任务中心同样支持重试。
5. GET /api/tenants/{tenantId}/production-tasks?type=VIRTUAL_AVATAR：统一任务中心列表。详情内容路径为 /{taskKey}/content。

## 结果与恢复

GetAsset 返回 Active 后，下载返回图片并保存到现有对象存储的 materials/{tenant}/{project}/virtual-avatars/...；保存成功后，同一事务更新任务 SUCCEEDED 和形态 virtual_avatar_asset_id。virtual_avatar_task 保存源图快照、火山素材组与资产 ID、结果存储路径、阶段和失败代码。未改变原图片展示地址。

只有原图仍一致时才绑定。重绘、上传替换和丢弃图片会清除旧资产绑定。Seedance 的 image_url.url 使用 asset://{assetId}，普通图片使用原有 URL。同一形态的元数据改名保留绑定。

保存失败、查询失败或超过 24 小时仍 Processing 的任务，重试继续查询已有资产，不重复创建。火山明确返回 Failed 的任务可重新入库。创建请求超时或响应不明确时标记 SUBMISSION_UNKNOWN，禁止自动重提或直接重试，需在火山控制台核实。服务重启可继续已持久化的任务；任务领取使用租约防止多个实例重复处理。数据库迁移为 V149、V150。

## 真实联调结果（2026-10-26）

用户已将 AK/SK 配置到项目根目录 env。本次使用实际 ArkAssetClient、VirtualAvatarService、VirtualAvatarStorage、ObjectStorageService 与 ProductionTaskService，完成一张角色形态图的真实外部接口及存储联调；没有 mock 火山客户端或 COS 存储。

| 项目 | 实测结果 |
| --- | --- |
| 火山项目 | default |
| 素材组 | group-20261026154446-gdhwn |
| 素材资产 | asset-20261026155020-sfm2z |
| 源角色形态 / 图片结果 | 5495 / 1571 |
| 本地任务 | VIRTUAL_AVATAR:202610260001 |
| 状态 | 创建受理 → 查询 Active → 保存结果 → SUCCEEDED |
| 保存对象 | materials/37/66/virtual-avatars/202610/202610260001/v1/original.png |
| 保存大小 / 类型 | 2,195,706 字节 / image/png |
| 任务中心 | 列表有记录、详情可读、状态 SUCCEEDED |
| 本地形态绑定 | virtual_avatar_asset_id 已绑定上述素材资产 |
| Seedance 引用值 | asset://asset-20261026155020-sfm2z |

15:50（北京时间）完成。保存走真实 COS PUT 及对象元数据验证，额外 HEAD 确认大小大于 0。脱敏回执见 outputs/virtual-avatar-live-20261026.json。单次真实链路测试 1 项通过，执行进程退出码 0。

## 验证边界与运行位置

火山素材组、素材 ID 及 COS 对象是真实资源，保留供后续联调复用。业务记录使用隔离的本地 H2 文件数据库 backend/.temp/avatar-live-db；仅复制了一张源形态的必要元数据。本地身份与权限上下文由测试提供，任务中心的聚合、列表和详情使用实际业务服务。没有部署或重启线上服务，没有修改线上数据库中的角色绑定；这条测试任务不会出现在现有线上任务中心。

本次未发起 Seedance 视频生成，asset:// 引用转换已由已有适配器测试覆盖；真实视频推理仍需后续单独验证。正常前端登录、线上迁移和发布也不属于本次接口实测。

此前后端 6 类共 59 项定向测试、前端相关 89 项测试已通过，npm run lint 通过（保留既有 warning），antd lint 无 deprecated/a11y 问题。普通自动测试使用本地 HTTP、H2 和存储 mock；上述单次真实链路验证另外使用用户提供的 AK/SK 及线上实例角色的临时 COS 凭据。临时凭据只经 SSH 读取并传递给测试子进程，未写入回执或源码。

此前线上只读检查未找到素材库 AK/SK，仅存在 VOLCENGINE_ARK 推理 API Key；历史检查回执保存在 outputs/virtual-avatar-credential-check-20261026.json。当前凭据来自用户新配置的本机 env，未同步至线上运行环境。

官方契约来源（2026-10-26 读取）：
- https://docs.volcengine.com/docs/ark/private-virtual-avatar-library-guide-preview
- https://docs.volcengine.com/docs/ark/create-asset-group-api
- https://docs.volcengine.com/docs/ark/create-asset-api
- https://docs.volcengine.com/docs/ark/get-asset-api
