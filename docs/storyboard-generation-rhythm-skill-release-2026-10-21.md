# 分镜生成节奏规则整合发布（2026-10-21）

## 发布结果

2026年10月21日15:49:46（北京时间），已将《分镜生成技能优化方案（生成阶段）》的创作规则整合到线上系统技能 `short-drama-storyboard-production`（短剧分镜制作）。

线上路径：`/opt/antv/shared/workflow-skills/short-drama-storyboard-production/SKILL.md`。

本次为持久化技能文件更新，采用修订核对、文件锁和原子替换。应用发布目录保持不变，服务进程保持运行。

## 整合内容

- 保留原文动作语义、关键因果和结果，允许压缩非关键过程。
- 按核心叙事重点组织内部镜头，相关动作、空间与情绪可由同一画面承担。
- 合并同一情绪的站位、手部与面部表演，保留原文已有的心理转折。
- 先规划剧情变化和声音，再划分生成单元，减少先拆散再填满时长的安排。
- 普通进出场可从动作中段或最后一步切入，与停步、对视和开口自然衔接。
- 对白、旁白和内心独白可与相关动作并行，必要强调与停顿仍留足时间。
- 增加拿衣离场、隐忍、情绪转折和走近对手四组镜头组织示例。

本次使用现有生成流程，未增加审查模型、评分或生成后审查环节。现有技能的保存前自检条款保持原文。

## 协议与本地文件

线上保留每条10—15秒、内部镜头1.5—4秒、素材引用、Schema 3及现有声音后端注入协议。

本地正式技能同步整合了相同创作规则，并保留工作区已有的声音片段协议改动。这些原有改动未随本次发布上传线上，因此本地与线上文件的完整内容和哈希不同。

- [本地正式技能](../backend/skills/short-drama-storyboard-production/SKILL.md)
- [本次线上发布内容](../outputs/storyboard-skill-rhythm-release-20261021/online-after.md)
- [线上变更差异](../outputs/storyboard-skill-rhythm-release-20261021/online.diff)

## 验证结果

- 线上文件回读与待发布内容一致；只变更此一个系统技能，其他26个技能文件哈希保持一致。
- 使用当前发布JAR中的 `SkillDocumentParser` 与 `FileSkillRepository`，在独立验证进程中两次读取实际线上文件，均成功解析并返回新版修订号。
- 前端HTTP 200；未认证技能接口HTTP 401，访问控制保持有效。
- `antv.service` 仍为active，PID在替换前后均为1197979；未重启服务或切换应用发布目录。
- 技能文件属主、属组和权限保持1000:1001、0640。
- 本次未发起新的付费分镜生成，未重生成或改写既有剧集分镜；实际创作效果需由后续新任务观察。

技能在新任务准备时读取并保存为运行快照。后续新准备的生成任务使用新版系统技能，已经冻结技能快照的运行继续使用其原快照；既有分镜不会自动改写。

## 修订与备份

原修订：`52fdea94f4c8077e85094c4f3d08244aea41f90aa75c4810229feaada4af4c86`。  
新修订：`b1b081894be9f6bef69751339bfe8f7bbc18b8c72b237a96b82e143031211423`。

线上备份目录：`/opt/antv/backups/20261021154946-storyboard-rhythm-skill`。

备份内保留 `SKILL.md.before` 与 `receipt.json`；已验证原文件哈希。需要恢复时，应先确认当前修订仍为本次新修订，在同一技能文件锁下原子恢复备份，避免覆盖后续编辑。

本地发布证据：

- [发布回执](../outputs/storyboard-skill-rhythm-release-20261021/publication-receipt.json)
- [线上回读与程序解析验证](../outputs/storyboard-skill-rhythm-release-20261021/online-verification.json)
- [备份核验](../outputs/storyboard-skill-rhythm-release-20261021/backup-verification.json)
