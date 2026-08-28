## Why

团队积分目前既能通过已支付套餐权益发放，也能由团队 Owner 直接手工增减，导致积分来源不可控、商业化权益与账本口径不一致。需要收敛积分获取规则，确保新增积分只能对应真实的套餐或订阅购买。

## What Changes

- **BREAKING** 移除团队积分手工调整 API `/api/tenants/{tenantId}/points/adjust`。
- **BREAKING** 移除团队设置页面的手工增加/扣减积分入口。
- 保留积分账户、流水、对账和 AI 消耗能力。
- 保留积分包一次性权益和订阅周期权益的自动发放，并确保重复发放幂等。
- 历史账本记录继续可查询，前端兼容展示已有手工调整记录。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `commercial-point-grants`: 积分发放来源限定为已支付套餐或订阅权益，禁止通用手工发放入口。

## Impact

- 后端 `TeamPointController`、`TeamPointService` 及其相关测试。
- 前端团队设置页面、积分服务调用和相关测试。
- 商业化权益发放服务与现有积分账本保持兼容，不修改支付确认和幂等发放流程。
- 旧客户端调用手工调整接口将收到 404，需要改用套餐购买流程。
