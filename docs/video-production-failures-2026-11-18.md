# 2026-11-18 线上视频失败排查

北京时间约 16:28–16:38，通过应用主机和数据库只读查询，以及火山素材 GetAsset、既有视频任务 GET 接口排查。未创建付费任务、修改线上配置或重启服务。

## 影响与已确认事实

- 当日视频表共有 13 条未删除记录，12 条 FAILED、1 条 SUCCEEDED。失败均位于 tenant 1、项目 92「未命名短剧」、第 2 集分镜 7 和分镜 8，使用 Seedance 2.0 Standard（model 5、provider 8、config 11）。
- 实际是 6 次原始提交及其 6 次自动恢复提交。原始任务 92、94、96、98、101、103 被 `InputImageSensitiveContentDetected.PrivacyInformation` 拒绝，服务商判断输入图片可能包含真人；这不等于图片确实为真人照片。
- 自动恢复分别创建 93、95、97、99、102、104，均在提交阶段报 `InvalidParameter`，提示 `asset-... is not found`。所有失败任务的 external_task_id 为空，未进入视频生成阶段。
- 火山控制面实时 GetAsset 查询确认 vwr4r、kgpxt、jq86g 三个被报不存在的素材均为 Active、ProjectName 为 default，无素材错误。不能将此故障归因为素材没有上传成功或已被删除。
- 12 条失败执行记录均为 RELEASED、settled_points=0，reserved_points 与 released_points 相同。
- 同模型任务 100 于 16:03 创建、16:08 完成，未使用 providerAssetId，证明该模型普通图片生成路径能够成功。
- antv.service 为 active；运行发布目录为 `/opt/antv/releases/20261117-wecom-customer-service`。

## 高度疑似原因：入库和推理身份的资源空间不一致

素材库客户端全局使用 ARK_ASSET_ACCESS_KEY / ARK_ASSET_SECRET_KEY，自动恢复没有按视频模型的 provider 选择素材库身份。视频推理则按各 provider 配置使用 API Key。

Standard 使用 config 11，mini 使用 config 9，两者 API Key 不同。对已有成功任务做交叉只读验证：

| 既有任务 | config 9 | config 11 |
| --- | --- | --- |
| mini 任务 91 | 200 / succeeded | 404 / ResourceNotFound |
| Standard 任务 100 | 404 / ResourceNotFound | 200 / succeeded |

这确认两组推理凭据不能互相访问既有任务。结合“控制面素材 Active，但 Standard 推理提示素材不存在”，高度疑似当前全局素材库身份与 Standard 推理凭据不在相同账号或授权资源空间。交叉任务查询不能单独证明 AK/SK 归属哪个账号；仍需核对控制面 AK/SK 与 Standard API Key 的账号、项目、授权范围后确定具体配置差异。

## 建议处理

先核对并统一 Standard 视频推理与素材入库所用账号及项目授权。若确需支持多账号模型，素材库客户端与资源缓存应按 provider 账号隔离，不能跨账号复用 asset ID。核对完成后再选单个失败分镜验证，不宜继续批量重试当前配置。

恢复表的 SUCCEEDED 当前仅表示已创建关联重试视频，未表示最终视频成功；排查时必须继续检查 retry_task_id 指向的视频状态。

证据保存在 `.temp/video-failures-20261118.jsonl`、`.temp/video-failure-details-20261118.jsonl`、`.temp/video-failure-models-20261118.jsonl`、`.temp/video-asset-probe-20261118.jsonl`。其中历史快照含带签名的素材链接，仅供本地排查，不应提交或对外分享。
