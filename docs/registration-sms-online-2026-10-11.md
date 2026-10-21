# 线上注册短信接通记录（2026-10-11）

目标环境：`https://antv.aixmax.cn`。

## 排查结果

- 注册页面已调用 `POST /api/auth/verification-code/register`，后端通过腾讯云发送验证码；注册接口验证验证码后创建账户。
- 验证码为 6 位数字，有效期 5 分钟，发送间隔 60 秒，最多允许 5 次错误尝试，验证成功后失效。
- 线上 `antv.service` 从 `/opt/antv/current/backend` 启动，读取链接到 `/opt/antv/shared/env` 的配置文件。六项短信配置在操作前与本地 `backend/env` 一致。
- 模板 `2737975` 原状态为 `-1`（审核拒绝）；审核意见要求删除验证码正文中的品牌推广内容。
- 原配置签名未匹配账户签名列表。已生效签名包括“广州信计网络”（723411）和“广州信计网络科技”（723373）。

## 已执行操作

1. 修改现有模板 `2737975`，类型为国内验证码短信，正文为：

   > 您正在注册账号，验证码：{1}，5分钟内有效。如非本人操作，请忽略本短信。

   提交请求 ID：`57cb4648-b9e9-4910-a4e9-be3da6d152dc`。腾讯云已接受提交，回读状态为 `1`（审核中），尚未生效。

2. 仅将线上 `TENCENT_SMS_SIGN_NAME` 更新为“广州信计网络”，核对其他配置原值不变。备份保存在服务器：

   `/opt/antv/shared/env.before-registration-sms-20261011T050113Z.bak`

3. 确认 `ai_execution_task`、`script_analysis_task`、`review_task`、`video_decomposition_episode` 的关键活动状态计数均为 0 后，重启 `antv.service` 加载签名。未部署代码或切换发布版本。

## 验证及剩余验收

- 本地后端 `AuthControllerTest`：6 项通过，短信发送器使用 mock。
- 本地前端注册页面与认证服务：2 个文件、7 项测试通过。
- 重启后线上首页及注册页返回 200，`/actuator/health` 返回 200，未登录访问 `/api/auth/bootstrap` 返回预期的 401；服务 `active/running`、`ExecMainStatus=0`、`NRestarts=0`。
- 上述测试不代表真实短信已发送或已送达。
- 腾讯云模板状态必须变为 `0`（已生效），`1`（审核中）和 `2`（审核通过待生效）均不能作为可发送依据。
- 模板生效后，使用用户在本次会话明确提供的测试手机号，通过线上注册验证码接口发送一条短信，并确认用户收到。手机号不写入本记录。
- 随后由用户在注册页输入验证码完成注册，验证成功后的登录会话及页面跳转。
- 本地 `backend/env` 尚保留原签名；本次仅接通线上环境。不要用整个本地配置覆盖线上配置。

密钥仅由环境文件读取，未写入本记录或 Git。

## 模板生效后的复验

用户告知模板生效后，再次通过腾讯云 API 核验模板 `2737975`：状态为 `0`，正文与提交内容一致，请求 ID 为 `e13b0a91-9152-48c6-bf84-cba6c800b755`。模板审核阻塞已解除。

本次尝试通过线上注册接口向会话中已授权的测试号码发送一条验证码，但本机 HTTPS 在 TLS 握手阶段超时，未发出业务请求。改从服务器调用时，SSH 在 banner exchange 阶段超时，远程发送脚本未执行。直接指定服务器真实 IP 的 HTTPS 探测也超时，因此不能把问题归因于 DNS，更不能据此断言线上服务已经停止。

腾讯云按测试手机号查询最近十分钟送达记录为空（请求 ID：`b4091bee-6b0f-4b1f-8c68-8d60b04ae564`）。本次未观察到成功发送或送达，仍需在网络恢复后执行线上接口实测。

## 用户反馈发送失败后的排查与修正

服务器连接已恢复。使用线上发布包中的 Spring Boot 3.3.5 `PropertiesPropertySourceLoader` 独立读取实际 `/opt/antv/shared/env`，发现直接写入 UTF-8 的中文签名被解析为 18 个字符，不能匹配 6 个汉字的已审核签名。但此探针没有还原 systemd 环境变量优先级，不能代表运行中应用的最终配置；当时据此作出的编码修正判断不完整，已在后续复验中纠正（见本文最后一节）。

仅将线上签名的文件值改为 Java Properties Unicode 转义：

```properties
TENCENT_SMS_SIGN_NAME=\u5e7f\u5dde\u4fe1\u8ba1\u7f51\u7edc
```

原配置备份：`/opt/antv/shared/env.before-sms-encoding-20261011T112156Z.bak`。已核对其他配置文本不变。相同 Spring 读取探针修正前匹配失败、长度 18；修正后匹配成功、长度 6。再次确认四类关键任务活动计数为 0 后重启服务；服务恢复 `active/running`、`ExecMainStatus=0`，健康接口返回 200 / UP。`env.example` 增加中文签名的编码说明；真正的操作系统环境变量应使用正常 Unicode 文本，不使用此转义。

随后通过线上注册接口发送仍返回 400 / `VALIDATION_ERROR`。使用线上同版本腾讯云 Java SDK、同一配置、同一已授权测试号码进行独立发送诊断，获得：

- 错误码：`LimitExceeded.PhoneNumberDailyLimit`。
- 原因：单一手机号每日短信发送次数超过上限。
- 请求 ID：`1ffea375-3709-433f-a1eb-7fdbc8d0df1a`。
- 最近 24 小时送达回执查询为空（请求 ID：`aa774111-94cb-4908-8358-9c3a5b5bab5f`）。限流结果不代表此前短信已送达，不能据此推算送达数量。

独立诊断请求同样被拒绝，没有获得成功发送证据。保留腾讯云现有发送限额，等待用户提供另一个已授权、未注册的测试号码，或当前号码额度恢复后，再通过线上接口进行真实验收。原先被统一提示隐藏的历史错误无法仅凭本次诊断回溯确认；本次已证实签名解析问题和当前号码限流两个阻塞。

用户随后提供第二个授权号码，线上注册验证码接口返回 409 / `DUPLICATE_MOBILE`（该手机号已注册）。此次在业务校验阶段被拒绝，未触发短信发送；没有修改现有账户数据。仍需一个未注册且未触发发送限额的授权测试号码。

## 第三个授权号码验证：短信套餐不足

用户提供第三个测试号码后，线上注册验证码接口仍返回 400 / `VALIDATION_ERROR`。线上同版本 SDK 的独立诊断明确返回：

- `FailedOperation.InsufficientBalanceInSmsPackage`：短信套餐余量不足。
- 腾讯云请求 ID：`45420706-0d97-4e90-8bc7-ea1253e43bf0`。
- 对配置应用查询 2025-01-01 至本次核验时刻所在整点之间创建的套餐，`SmsPackagesStatisticsSet` 为空；查询请求 ID：`1f6d0e8e-13fc-486f-bb11-733601754db1`。该查询有创建时间范围，不能据此推断账户所有历史套餐或云账户现金余额。

本次未发送成功。当前需要为此腾讯云账号/应用补充适用的国内短信套餐，再通过线上注册接口对第三个授权号码复验。未购买套餐、调整计费方式或关闭发送限额；不能仅通过继续更换手机号解决套餐不足。

## 购买生效后的复验与最终配置纠正

用户确认套餐购买生效后，腾讯云 Java SDK 诊断请求返回 `Ok / send success`（请求 ID：`393d9a9c-e2fb-4f9d-a427-82d357419e68`）。随后按该测试手机号查询到 `ReportStatus=SUCCESS`、`Description=DELIVRD` 的送达回执（查询请求 ID：`55ce5004-fcf3-4d93-9555-9e5bbfa21444`）。这证明套餐可用且诊断短信已送达；诊断验证码没有进入应用验证码缓存，不能用于网页注册。

线上接口仍失败时，进一步核对发现 `antv.service` 同时配置了 `EnvironmentFile=/opt/antv/shared/env`。此前写入的 Unicode 转义被 systemd 去掉反斜杠，进程实际收到 `u5e7fu5ddeu4fe1u8ba1u7f51u7edc`。只验证 Properties 读取器遗漏了这一层，错误修正已撤回。

最终线上配置恢复为 UTF-8 中文原值：

```properties
TENCENT_SMS_SIGN_NAME=广州信计网络
```

纠正前备份：`/opt/antv/shared/env.before-sms-systemd-fix-20261011T114705Z.bak`。仅修改签名，其他配置未变；无活动任务时重启后，验证了以下两层：

- 新 Java 进程 `/proc/<pid>/environ` 中签名与已审核签名完全一致，长度为 6。
- 独立启动环境探针继承运行中 Java 进程的环境，使用线上同版本 Spring、同一 application.yml、同一工作目录，在环境准备完成后立即退出，不创建业务 Bean。最终 `sms.tencent.sign-name` 与已审核签名一致；`systemEnvironment` 的值优先于导入文件的值。
- 健康接口返回 200 / UP。

`env.example` 已区分两种加载方式：生产 systemd EnvironmentFile 使用 UTF-8 原文；仅通过 Spring Properties 加载时才使用 Unicode 转义，或直接提供正常 Unicode 的操作系统环境变量。不得将两种方式混用。

最终注册接口复验仍被腾讯云手机号每日限额阻断；继承运行中环境、解析相同 Spring 配置的诊断返回 `LimitExceeded.PhoneNumberDailyLimit`（请求 ID：`497772e9-d441-4ff6-8363-1fd93c79d35e`）。已停止继续发送，保留原限额。

当前结论：模板已生效、套餐已可用、诊断短信有成功送达回执、运行时签名已核对正确；通过线上注册接口发送成功并完成注册的最终验收仍需等待该号码额度恢复，或使用另一个授权的未注册且未限流号码。
