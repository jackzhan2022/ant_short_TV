## Why

后台多个页面将后端枚举值直接展示给用户，出现 `PENDING`、`GLOBAL_DISCOUNT`、`VIDEO_UNDERSTANDING` 等英文编码；同时部分字段标题混用 Code、Base URL、API Key、JSON、ID，降低中文后台的可读性并造成操作误解。现在统一字段字典，可以在不改变接口契约的前提下消除用户可见的英文枚举和技术字段标题不一致问题。

## What Changes

- 新增统一的后台字段显示字典，覆盖状态、服务类型、审核类型、剧本来源、生产阶段、计费指标、商业权益、文件格式等用户可见枚举。
- 将所有后台页面中直接渲染的英文枚举转换为中文名称，未知枚举使用稳定的中文兜底文案，不裸显英文编码。
- 将后台字段标题统一为中文，如“服务商编码”“接口地址”“密钥”“扩展配置”“项目编号”等；表单提交字段名和后端接口参数保持不变。
- 保留模型编码、项目编码、错误码等确有排查或复制价值的业务数据值，但通过中文标题和辅助展示区分其技术属性。
- 为字段字典和关键页面显示增加测试，覆盖已知值、未知值和常用页面路径。

## Capabilities

### New Capabilities

- `backend-field-dictionary`: 提供后台用户可见字段和枚举的统一中文显示约定。

### Modified Capabilities

<!-- No existing OpenSpec capability requirements are being changed. -->

## Impact

- 影响 `frontend/src/pages` 和 `frontend/src/components` 中的表格、表单、详情抽屉、标签和任务状态展示。
- 新增前端共享字典工具及单元测试，不修改后端 API、数据库字段、自动生成服务类型或接口枚举。
- 可能需要更新现有页面测试中对英文展示文本的断言。
