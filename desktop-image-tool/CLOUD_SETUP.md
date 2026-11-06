# 配套云端接入

桌面资产能力复用现有 Java 后端账号、团队、项目权限，与运维监控一并发布。发布结果见 docs/operations-desktop-production-release-2026-11-05.md。

## 部署步骤

1. 按已有后端流程构建发布包含 com.antshorttv.desktop 的版本；上线前按现有流程备份数据库、核对 Flyway 顺序。
2. 应用启动时按顺序执行运维 V174/V175 和 V176__desktop_source_assets.sql，后者新增来源项目及资产名称表。新增迁移已在隔离 MySQL 8.0、生产库结构副本上验证；线上执行结果由发布记录确认。
3. 复用现有 HttpOnly 登录 Cookie、XSRF-TOKEN / X-XSRF-TOKEN、团队成员与 ELEMENT:VIEW 项目权限。X-Tenant-Id 本身不是授权。
4. 客户端默认连接 https://antv.aixmax.cn，界面无需填写平台地址。已有空地址配置会自动升级，保留连接编号和同步间隔；本地开发夹具仍可在配置文件中使用 HTTP localhost/127.0.0.1。

接口前缀 /api/desktop/source-projects，使用现有 ApiResponse：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | / | 当前团队来源项目 |
| POST | /sync | 名称清单幂等同步 |
| POST | /sync-current | 完整更新当前清单并停用同项目历史清单（0.2.7） |
| GET | /{id}/assets | 资产及匹配状态 |
| PUT | /{id}/binding | 绑定目标项目 |
| POST | /{id}/match | 精确匹配 |
| GET | /{id}/candidates | 同项目/类型/主体候选 |
| PUT | /assets/{id}/match | 人工匹配 |
| POST | /prompts | 校验关系并读取已有完整提示词 |

sync 仅保存项目名称及稳定编号，以及资产 sourceAssetId/sourceParentId/name/type。完整范围成功才标记遗漏资产失效；部分同步保留旧项。每个项目最多 5000 个来源名称；一次读取最多 500 条提示词。不新增剧本、图片或来源凭据存储。

0.2.7 客户端手动和自动同步只调用 `/sync-current`，要求 `complete=true`。同团队、连接、来源团队与项目内在同一事务中激活当前 ScriptId、停用其他清单及其资产，失败回滚。唯一有效项目绑定可继承；不同清单的资产匹配不自动复制。停用清单从项目列表隐藏且拒绝读取、绑定、匹配和获取提示词。既有 `/sync` 仅保留旧客户端兼容；旧客户端仍可能上传历史范围，应升级至 0.2.7。本次无数据库迁移。

## 后续真实验证

- 验证实际剧智创登录会话、CSRF、多团队选择、成员撤销与项目越权。
- 手动登录火山剧创，核对实际团队/项目/剧本列表动作、范围字段、签名及分页总数，不符合当前支持契约时补充适配后再声明全量同步可用。
- 核对三类资产及变体真实 ID、父子关系、分页数量；只允许上传投影后的名称字段。
- 核对已有主体/变体提示词组合。真实生图使用个人 Key，实际调用费用由用户控制。
- 核对分组输出、重名追加序号、登录失效和同步失败。不要记录 Cookie、Key 或剧本正文。

测试命令：在 backend 运行 `mvn -q '-Dtest=DesktopSource*Test' test`。集成测试使用 H2，不操作生产库。
