# 资产识别批量查重发布

2026-10-19（北京时间）发布至 antv.aixmax.cn，服务于 16:49:14 进入 active 状态，健康检查 UP，自动重启次数 0。

## 变更与校验

- 功能提交 `a37cda197ce23c7e66d62e1ca83f685f628a5cdf`，已推送 master。批量协议沿用 `search_script_assets`，一次接收 1–50 个 typed queries；保留单项查询和分页兼容，维持精确身份匹配、别名和歧义校验。
- 新发布目录：`/opt/antv/releases/20261019163711-a37cda1-asset-batch-lookup`。上一发布 `/opt/antv/releases/20261019145832-e8260a1-asset-lookup-tools` 保留用于回滚。
- 数据库、技能和运行环境备份：`/opt/antv/backups/20261019163711-a37cda1-asset-batch-lookup`。数据库压缩包和技能归档均已验证。
- 后端 JAR SHA-256：`beba3125c2e6ca7701a0a4ddca9f746284c711a6e57fdef73911529432e2a783`。从已推送提交独立构建，1,261 个应用类与已通过测试的版本一致。
- 200 项后端回归通过；前端 Biome、TypeScript、antd 检查通过，保留既有警告。前端无改动，发布目录复用上一版静态资源。
- 首页、项目页和静态资源正常；数据库保持 V140，无新增迁移。资产 Agent 输出预算 32768、执行预算 12 步及已发布模型参数均不变。
- 仅资产识别技能追加 v2 批量协议，保留原有内容；其他 32 个技能文件哈希一致。运行环境哈希一致。发布验收期间任务、模型调用和积分流水未变化。

## 项目 50 在线重试

用户授权后，通过现有登录页面确认积分预估，于 16:52:02 发起角色场景道具识别阶段重试；分析任务 47、执行 178613、阶段第 6 轮。复用 37 集已成功结果，重试其余 33 集，概要 70/70 保留。

16:52:19 的首次只读检查已确认真实模型成功执行 3 次批量查询，共 13 个名称，最大一批 7 个。累计实际净扣费为 936.71058474，预占为 63.28941526；预占不计作实扣。费用范围为 `tenant_id=39 AND workbench_project_id=50`，实扣使用 `settled_points-refunded_points`。

重试仍在运行；本记录不代表全项目已完成。后续状态保存在本地忽略目录 `outputs/asset-batch-retry-current.json`。

发布清单、部署回执和线上验收保存在发布目录及 `/home/ubuntu/antv-20261019163711-a37cda1-asset-batch-lookup`。本地验收见 `outputs/asset-batch-release-online-verification.json`。
