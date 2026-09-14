# 生产任务来源覆盖与边界

任务中心读取已有领域记录，不维护第二套可变生命周期。没有 AI execution 的历史记录仍能使用领域状态查询；无法提供的进度保持 null。

| 类型 | 稳定身份 / 权威状态 | 粒度与入口 | 本人控制 |
|---|---|---|---|
| VIDEO_DECOMPOSITION | video_decomposition_batch.id；子项聚合 | 顶层拆剧批次；拆剧工作台 | 查看子项，无批次操作 |
| VIDEO_EPISODE | video_decomposition_episode.id；领域 status | batch_id 子项；拆剧工作台 | 失败、retryable、无执行锁、无不可变结果、有 execution，委派领域 retry |
| STORYBOARD_BATCH | storyboard_batch.id；item execution 聚合 | 顶层分镜批次；项目分镜 | 查看子项，无批次操作 |
| STORYBOARD_ITEM | storyboard_batch_item.id；execution | 批次创建人为提交人；项目分镜 | execution 也属于本人且有 AI_SERVICE:USE + STORYBOARD:AI_BREAKDOWN 时取消/失败重试 |
| SCRIPT_OPERATION | script_ai_operation.id；execution，领域 fallback | 剧本生成/改写、资产提取、范围重提取、分镜、提示词 | 本人 + 相应领域权限 + AI_SERVICE:USE，复用执行取消/重试 |
| SCRIPT_ANALYSIS | script_analysis_task.id；领域 status/overall_progress | 独立历史分析；项目剧本 | 查看；现有 retryAnalysis 仅以当前任务/阶段为目标，不能安全绑定任意历史 taskKey |
| REVIEW | review_task.id；领域 status/overall_progress | review_project.main_project_id 为主项目；审核详情 | 复用 ReviewWorkbenchService 取消/失败重试，未绑定稿按原所有者/编辑权限校验 |
| IMAGE | ai_image_task.id；execution，领域 fallback | 独立图片；分镜或设定库 | 取消；普通图片成功后再生成，复用领域幂等与新版本链路 |
| VIDEO | ai_video_task.id；execution，领域 fallback | 独立视频；项目视频 | 取消；再生成留在原业务入口 |

## 异步能力缺口

ShotProductionService.createVoiceTask 同步生成 LOCAL/PLACEHOLDER 输出；createComposeTask 和 createEpisodeComposeTask 在请求返回前同步写入结果。三者没有后台 dispatcher/lease。本次不将它们包装成可恢复后台任务。后续独立变更需提供持久提交、后台执行、恢复、取消与真实结果，再接入中心。

## 控制边界

团队 OWNER 和当前有效系统 ADMIN 能看团队任务；所有身份对其他成员任务均只读。业务权限另外决定结果入口。

图片 Idempotency-Key 必填，按团队、源 taskKey 与客户端键隔离。网络失败后客户端复用键，响应只返回新 taskKey 的安全摘要，保留旧结果和执行版本关联。

VISUAL_VARIANT 图片的现有 regenerate 会替换当前生成所有者，可能间接取消别人任务，所以中心不开放该动作，需要领域原子所有权保护后才可开放。现有视频 regenerate 只有活动 request hash 去重，没有持久幂等键与源版本关联，中心也暂不开放视频再生成；原业务入口保留。

审核/拆剧单集重试委派原服务，保持成功兄弟项、计费快照与业务身份。不直接生成脱离领域记录的 execution，不承诺供应商取消或全额退款。

## 归属与历史

软删除图片/视频任务、审核稿、拆剧批次被排除。主项目删除或权限撤销后保留受限摘要，不返回项目标题、进度、结果链接或控制。无可靠父关系的图片/视频独立展示，不根据时间猜批次。Agent run、调用及 attempt 不成为根任务。

分镜 admission 可以让 B 批次复用 A 已发起的 execution。item 使用稳定 item ID 和 B 的提交身份，复用 A 执行时只读；A 原 operation 继续独立可见。同创建人的 operation 折叠到批次，但旧 operation taskKey 仍可直接打开。

## 状态与元数据

排队/运行映射来自领域真实状态；只有 PENDING_REVIEW/WAITING_CONFIRMATION 映射人工关卡 WAITING_USER。SUCCEEDED/SUCCESS/COMPLETED/CONFIRMED 及成功带警告均属 SUCCEEDED；超时/校验失败属 FAILED。未知历史状态归入 FAILED 并提示无法识别，不伪造人工关卡。

批次按运行、排队、人工关卡优先，再依照子项成功/失败/取消计数确定终态。真实完成比例不等于成功率。缺失进度保持 null。分镜警告根据当前页相关 execution/run 和持久 shot_plan_json 的非空 warnings/classificationWarnings 数组存在性富化，只返回标识，不拉完整正文，不替代领域警告计数。

图片/视频入口批量核验目标资产/分镜存在且未删除，并要求 AI_IMAGE_TASK:VIEW / AI_VIDEO_TASK:VIEW；不能访问时不返回链接。未绑定审核稿还核验原稿所有者或团队读取权限。

## 查询契约

/api/tenants/{tenantId}/production-tasks 返回 items/total/page/pageSize/canViewTeamTasks；/summary 返回 total/counts；/{type:id} 返回安全摘要；/{type:id}/children 返回分页子项。筛选 scope/type/statusGroup/projectId/creatorId/createdFrom/createdTo。mine 创建人不能被参数覆盖。默认 20、最大 100，服务端排序分页。

每个来源显式下推 tenant，每类批次只聚合自身子来源。当前页项目、创建人、警告、媒体目标批量富化，不逐行查完整详情；表名来自固定代码，不接受客户端输入。
