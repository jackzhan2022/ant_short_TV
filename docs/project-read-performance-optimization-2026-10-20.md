# 项目详情与剧本页读取优化

日期：2026-10-20。对应 `GET /api/projects/52` 和 `GET /api/projects/52/script-page-workspace`。修改仅在本地，未部署或重启线上服务。

## 修改

- 项目权限解析改用不含 `initial_script_content` 的查询，保留封面、名称、视频配置等调用方需要的项目元数据。项目详情的完整读取保持原有字段。
- 同一 GET 请求内按用户、团队、项目复用已解析权限。每次调用仍检查团队及成员有效性；新请求重新解析，写请求不复用。
- 剧本版本列表只查询响应需要的七个字段，保留团队过滤、版本倒序。单版本详情仍读取正文。

## 线上只读 SQL 对照

通过部署主机上的同一个 MySQL CLI 连接，在只读事务内交替执行完整查询和精简查询，各三次。仅输出结果字节数与耗时，不保存正文。耗时包含读取查询结果和结束标记的额外往返，不包含应用连接池、Java 映射或 HTTP 响应。

| 查询 | 原结果字节数 | 精简后字节数 | 原耗时 | 精简后耗时 |
| --- | ---: | ---: | --- | --- |
| 项目 52 完整行 / 权限元数据 | 262,794 | 321 | 575.78～638.40 ms | 8.53～8.56 ms |
| 当前剧本完整版本列表 / 摘要列表 | 262,561 | 71 | 477.80～617.39 ms | 8.53～8.63 ms |

结果体分别减少约 99.88% 和 99.97%。字节数为 CLI 文本输出大小，包含分隔符。项目元数据对照排除正文列；版本摘要对照选择与代码一致的七列。此结果支持减少无用大字段传输，不能换算成整个接口的提速比例：详情和剧本页仍需读取各自实际展示的正文。

原始数据：`outputs/project52-read-optimization-readonly-20261020.jsonl`。

## 验证

首轮 `ProjectAccessResolverTest`、`ProjectReadPerformanceIntegrationTest`、`ScriptWorkflowReadBoundaryTest` 共 14 项通过。集成测试使用长正文数据核对实际 MyBatis SQL、跨团队过滤、版本排序及完整正文读取兼容性。

最终相关回归共 73 项：71 项通过，2 项错误，0 项断言失败。结果如下：

| 测试类 | 用例数 | 结果 |
| --- | ---: | --- |
| ProjectAccessResolverTest | 14 | 全部通过；覆盖 GET 复用、用户/团队/项目隔离、下一请求重新校验、团队成员移除及写请求不缓存 |
| ProjectReadPerformanceIntegrationTest | 2 | 全部通过 |
| ScriptWorkflowReadBoundaryTest | 1 | 通过 |
| ProjectAccessibleQueryIntegrationTest | 3 | 全部通过 |
| ProjectCoverImageServiceTest | 17 | 全部通过 |
| ProjectControllerTest（源文件为 OrganizationProjectControllerTest.java） | 8 | 全部通过 |
| ScriptWorkflowControllerTest | 28 | 26 项通过；2 项在初始化 Agent 时发生重复 code 错误 |

两项错误为 `createsAndReanalyzesInitialScriptTasks` 和 `automaticStoryboardResumesFundsAndRecoversSubmissionWithoutDoubleReservation`，均提示 `Agent code 已存在。`。将本次三个生产文件替换为 HEAD 原始版本，在独立编译输出副本中复跑上述两个方法，均复现相同错误；因此未修改本次范围外的工作流初始化逻辑。此基线只撤销本次三个文件的优化，其余代码仍为当前工作区。

常规 Maven 回归曾在测试编译阶段被并行新增的监控测试阻断：当时缺少 `MonitoringProperties`、`MonitoringEvent`，之后另有 `MonitoringApiExceptionResolver` 等类尚未补齐。共享构建输出也曾出现类缺失。最终通过临时 POM 使用独立构建目录，并仅排除 `com/antshorttv/monitoring/**` 测试源码；其余生产与测试代码仍使用当前工作区。原始 POM 和监控模块未修改，临时 POM 在验证结束后删除。本次并非全量测试通过。

独立复核未发现需要修正的权限或字段兼容问题。相关已跟踪文件通过 `git diff --check`；新增测试和文档检查无行尾空格。

验证记录：

- `backend/target/project-read-regression-final.log` 及 `backend/target/project-read-validation/surefire-reports/`：65 项，63 通过、2 错误。
- `backend/target/project-read-controller-regression.log`：项目控制器补充 8 项通过。
- `backend/target/project-read-baseline-failures.log` 及 `backend/target/project-read-baseline/surefire-reports/`：优化前代码复现上述 2 项错误。
