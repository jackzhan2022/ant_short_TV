# 剧本自动命名与历史补全

## 规则

1. 保留已填写的人工项目名称和剧本标题；两个字段分别判断，仅空名称、“未命名短剧”和“未命名剧本”需要补名。
2. 优先提取原文的明确剧名，包括剧名/片名标签、开头独占一行的书名号标题、明确的短剧标题行。
3. 未找到明确剧名时，将剧本前 5000 个 Unicode 字符交给已有默认文本模型生成剧名。不会发送后续正文。
4. 没有有效文本、模型失败或返回无效名称时保留原名。数字占位文本不调用模型。
5. 自动命名免费，不创建积分预扣、收费执行或结算；线上功能的模型调用使用 `script_auto_title_free` 标识记录日志。

## 新剧本

项目带初始剧本创建、手工保存、AI 生成/改写和版本应用成功后，通过事务事件异步补名。事务回滚不触发，命名失败不影响剧本保存。

调用模型时不持有命名写入锁；写回时锁定并核对正文、比较原名称，保留生成期间发生的人工改名。更新后失效剧本缓存。

代码修改未发布到线上，需随后端版本发布后才对后续创建/保存自动生效。本次没有发布整个工作区或重启线上服务。

## 历史执行结果

2026-11-18，在应用主机使用现有数据库和文本模型执行了一次补全；没有扣除用户/团队积分。

- 候选：57 个项目。
- 完成补名：52 个项目，其中 8 个提取原文剧名，44 个按剧情生成。
- 实际更新：46 个项目名称、42 个剧本标题。
- 保留：17 个人工名称字段。
- 跳过：项目 15、16、17（仅数字占位文本），39（婚恋观点文案，未生成有效剧名）、46（模型标识文本）。
- 逐项回读校验：名称符合清单，人工名称保持原样，正文 SHA-256 不变，无不匹配项。

示例：项目 92 为《拒嫁摄政王后他急了》；项目 74、78 从原文提取《旧车开进好日子》；项目 80、81 提取《第37次测试》《玉镯奇案》，同时保留原人工剧本标题。

原值、新值、正文哈希与生成来源清单保存在应用主机 `/home/ubuntu/antv-script-titles-20261118-reviewed-plan.json`（权限 0600）；本地副本位于 `.temp/script-titles-20261118-reviewed-plan.json`。清单不包含剧本正文或服务凭据。

## 维护命令

验证通过：`ScriptTitleRulesTest` 4 项、`ScriptAutoTitleServiceTest` 6 项、`ProjectControllerTest` 10 项、`ScriptWorkflowControllerTest` 30 项，共 50 项，零失败。维护脚本通过 Python 语法检查，并完成线上逐项回读校验。

首轮回归遇到构建目录残留的旧 `V178__customer_service_configuration.sql`，与源代码现有 V179 重复建表；清除该无对应源文件的生成产物后重跑通过，没有修改数据库迁移源文件。

在应用主机运行，凭据从既有 env 文件读取，不写入脚本或输出：

```sh
python3 scripts/backfill-script-titles.py
python3 scripts/backfill-script-titles.py --generate /private/path/title-plan.json
python3 scripts/backfill-script-titles.py --apply /private/path/title-plan.json
python3 scripts/backfill-script-titles.py --verify /private/path/title-plan.json
```

默认只预检。生成和应用分离，应用时对原名及正文哈希做并发检查。重复应用同一清单不会再次改名。仅缺少有效剧情的遗留记录会继续显示为未命名。
