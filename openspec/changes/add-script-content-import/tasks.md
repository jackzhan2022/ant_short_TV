## 1. 公共剧本文档解析能力

- [x] 1.1 为 TXT、Markdown、Docx、空文件、不支持格式和损坏 Docx 编写 `ScriptContentParser` 失败测试，并确认测试因公共解析器尚不存在而失败
- [x] 1.2 实现无状态 `ScriptContentParser`，使用 UTF-8 读取 TXT/Markdown、使用 Apache POI 提取 Docx 段落，并统一校验非空内容与支持格式
- [x] 1.3 将 `ReviewWorkbenchService` 的现有文件提取逻辑替换为公共解析器调用，删除仅由旧实现使用的私有方法和 import
- [x] 1.4 运行公共解析器和 `ReviewWorkbenchControllerTest`，确认新增测试与既有审核导入回归测试全部通过

## 2. 无持久化解析接口

- [x] 2.1 编写 `POST /api/script-content/parse` 集成测试，覆盖登录与租户上下文、Docx 成功解析、不支持格式、空内容，以及调用前后审核项目和版本数量不变
- [x] 2.2 新增解析响应类型和 `ScriptContentController`，仅调用公共解析器并返回文件名与文字，不注入 Mapper、存储客户端或审核服务
- [x] 2.3 运行解析接口测试，确认成功响应、统一业务错误、未登录拒绝和无持久化断言全部通过

## 3. 前端导入服务与独立组件

- [x] 3.1 在编写 Ant Design 组件前运行 `npx antd info Dropdown Upload Modal Collapse Radio`，按当前 antd v6 元数据确认 API
- [x] 3.2 为 `ScriptContentImport` 编写失败测试，覆盖导入菜单、accept 类型、TXT/Markdown 浏览器读取、Docx 后端解析、空内容和请求失败不覆盖草稿
- [x] 3.3 在短剧创作 `service.ts` 中新增 Docx 解析请求，并复用独立审核模块的项目列表、项目详情、版本类型和查询函数
- [x] 3.4 实现导入菜单和本地文件分流：TXT/Markdown 使用 `File.text()`，Docx 调用解析接口，其他格式在前端拒绝
- [x] 3.5 为审核版本选择编写失败测试，覆盖项目列表加载、按项目懒加载全部版本、选择非最新版本、加载失败重试和确认回填
- [x] 3.6 实现审核剧本版本弹窗，以项目为分组懒加载详情并缓存版本，每个版本作为独立可选项且显示剧本名、版本号、文件名和创建时间
- [x] 3.7 为已有草稿覆盖保护编写失败测试，并实现统一候选内容入口：非空校验、覆盖确认、取消保持原内容、成功后返回内容与当前页面来源提示
- [x] 3.8 运行 `ScriptContentImport` 测试，确认各来源、错误路径和覆盖保护全部通过且无未处理 Promise

## 4. 接入短剧创作页

- [x] 4.1 在创作页测试中先断言导入内容会回填、可继续编辑，且最终项目请求只提交编辑后的 `initialScriptContent`
- [x] 4.2 用 `ScriptContentImport` 替换页面内联上传逻辑，移除旧文件读取函数与文件名状态，并把提示文案限定为 `txt、md、docx`
- [x] 4.3 接收组件返回的内容和来源提示，同步更新 `scriptDraft` 与 `projectForm.initialScriptContent`，但不向项目创建请求增加来源字段
- [x] 4.4 调整最小必要样式，保持圆形导入入口、来源提示和审核版本弹窗与现有创作页视觉一致
- [x] 4.5 运行短剧创作页与导入组件测试，确认灵感列表、跳过上传、第二步配置和项目创建既有行为无回归

## 5. 完整验证

- [ ] 5.1 运行 `npm run backend:test`，确认全部后端测试通过
- [x] 5.2 运行 `npm run frontend:lint`、`npm run frontend:test` 和 `npx antd lint ./frontend/src`，修复本变更引入的类型、Biome、测试或 Ant Design 问题
- [x] 5.3 运行 `npm run frontend:build`，确认生产构建成功
- [ ] 5.4 使用本地前后端冒烟验证 TXT、Markdown、Docx 导入、指定审核版本引用、覆盖取消、导入后编辑和项目初始剧本内容
- [x] 5.5 检查最终差异，确认没有 PDF 入口、数据库迁移、文件持久化、审核数据创建、来源绑定或无关文件变更
