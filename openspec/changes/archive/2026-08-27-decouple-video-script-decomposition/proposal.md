## Why

视频拆剧当前在上传和创建批次阶段要求项目 ID，导致用户还没有决定导入哪个项目时无法先做视频分析，也会让上传失败被前端表现成模糊的 `Response status:0 / Network Error`。拆剧应先形成系统生成的分析批次，等用户审核草稿并确认导入时再选择项目。

## What Changes

- 视频上传接口不再要求 `projectId`，上传文件进入租户级、批次前置的拆剧素材路径。
- 创建拆剧批次不再要求 `projectId`，批次编号/批次归属由系统生成，前端页面不再展示项目 ID 输入。
- 批次列表、详情、单集详情支持租户级未绑定项目的拆剧数据；历史已绑定项目的批次仍可读取。
- 确认导入时才要求目标项目，并继续创建 `VIDEO_IMPORT` 剧本版本。
- 前端上传失败时给出明确网络/服务不可达提示，避免只显示 `Response status:0` 或英文兜底。

## Capabilities

### New Capabilities

- `video-script-decomposition`: 视频上传、租户级拆剧批次、单集分析草稿审核，以及确认导入项目的行为契约。

### Modified Capabilities


## Impact

- Backend: `VideoDecompositionController`, `VideoDecompositionService`, video decomposition DTOs, persistence reads/writes around nullable project linkage, controller tests.
- Frontend: `/video-script-decomposition` page, page-local service helpers/tests, global request error messaging.
- Data: existing project-linked video decomposition rows remain compatible; new rows may have no project ID until confirmation/import.
