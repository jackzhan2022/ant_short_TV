## Why

当前剧本审核页将导入、项目选择、版本编辑、任务配置和问题处理堆叠在同一页面中。审核人难以快速识别待办剧本，也需要在问题列表与正文之间频繁滚动，降低了处理效率和修订安全性。

## What Changes

- 新增独立的剧本库页面，集中展示独立剧本及其基于现有审核数据推导的工作状态和下一步操作。
- 将审核工作台收敛为剧本版本、问题队列、正文和问题详情的三栏处理界面。
- 将创建审核任务改为按需打开的配置弹窗，并保留现有版本、审核模式、维度和范围选择能力。
- 为问题处理提供正文定位、高亮、前端筛选、处理进度和修订预览入口。
- 复用现有项目、版本、任务、问题和批量修复接口；不引入后端接口、字段或持久化状态变更。

## Capabilities

### New Capabilities
- `script-review-library`: 提供独立剧本的检索、状态概览和进入审核工作台的前端入口。

### Modified Capabilities
- `script-review-workbench`: 调整审核工作台的信息架构和任务发起、问题定位与处理交互。

## Impact

- Affected code: `frontend/src/pages/script-review/`、前端路由与菜单配置，以及可能抽取的页面级展示组件和样式。
- APIs: 复用现有 `queryReviewProjects`、`queryReviewProject`、版本、审核任务、问题处理和报告导出接口；不修改后端 API 合同。
- Dependencies: 不新增运行时依赖。
