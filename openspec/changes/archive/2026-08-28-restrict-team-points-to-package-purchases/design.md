## Context

团队积分由 `team_point_account` 和追加式 `point_ledger` 统一记录。商业化订单确认后，`CommercialEntitlementOrchestrator` 与订阅周期发放服务通过 `PointAccountingService.grant` 入账；同时，团队 Owner 可通过 `TeamPointController` 的 `/points/adjust` 接口手工增减积分，团队设置页面也提供对应弹窗。

本变更要求收紧积分增加来源，但不能影响套餐支付、订阅周期发放、AI 消耗、流水查询和对账。现有数据库中的历史手工调整记录需要保留。

## Goals / Non-Goals

**Goals:**

- 移除手工积分调整的后端路由、服务入口和前端入口。
- 保证新增积分只能由已支付商业化权益发放产生。
- 保持商业化发放的账本一致性与幂等重试行为。
- 为旧接口和前端行为提供回归测试。

**Non-Goals:**

- 不修改套餐目录、支付确认、订阅生命周期或权益发放数据模型。
- 不删除历史积分账本记录或禁止历史记录查询。
- 不改变 AI 预扣、结算、退款及积分价格策略。

## Decisions

### Remove the adjustment endpoint

删除 `TeamPointController` 的 `POST /api/tenants/{tenantId}/points/adjust` 映射及 `TeamPointService.adjust` 方法，使旧调用自然返回 404。选择彻底移除而不是保留并返回业务错误，是为了避免任何客户端继续把手工调整当作合法积分来源；相比仅隐藏前端入口，后端删除能形成不可绕过的约束。

### Keep grant primitive private to commercial flows

保留 `PointAccountingService.grant`，因为商业化权益发放依赖它；不新增替代的通用授予接口，也不改变其幂等键处理。通过代码调用边界和测试约束，确保只有商业化发放服务调用该能力。

### Remove team-settings controls only

团队设置页面删除“调整积分”弹窗、服务导入和相关测试断言。余额、累计获得、累计消耗和流水表继续展示；历史 `ADJUST_GRANT`/`ADJUST_DEDUCT` 类型采用兼容文案或原始类型展示，避免旧数据渲染异常。

### Test at HTTP and UI boundaries

后端测试验证旧调整 URL 返回 404，并保留账户查询、AI 消耗和商业化权益发放测试。前端测试验证团队设置不再渲染调整按钮，同时商业化页面仍加载余额和权益发放记录。

## Risks / Trade-offs

- [Risk] 旧客户端依赖调整接口而出现 404。→ 将破坏性变更记录在发布说明中，客户端引导用户进入套餐购买流程。
- [Risk] 删除手工调整测试可能降低历史流水兼容覆盖。→ 保留流水查询测试，并覆盖未知/历史类型的展示。
- [Risk] 其他非商业化代码潜在调用 `grant`。→ 在实现前搜索调用点并限制变更范围，仅保留订单和订阅发放调用。

## Migration Plan

1. 部署后端删除路由和服务入口，并同步发布前端隐藏调整入口。
2. 验证已支付积分包、订阅周期权益各成功发放一次，重复重试不重复入账。
3. 验证旧调整 URL 返回 404，积分查询、流水、对账和 AI 消耗正常。
4. 回滚时恢复上一版本应用代码；不回滚或删除已有订单、权益发放和账本数据。

## Open Questions

无。
