# commercial-order-payment Specification

## Purpose
TBD - created by archiving change add-subscription-package-management. Update Purpose after archive.
## Requirements
### Requirement: WeChat native order
系统 SHALL 为团队管理员创建微信 Native 扫码订单，保存团队、套餐版本、价格快照和唯一商户订单号。

#### Scenario: Create order
- **WHEN** 有权限的团队管理员选择可售套餐
- **THEN** 系统创建待支付订单并返回微信支付二维码参数

### Requirement: Idempotent payment confirmation
系统 SHALL 以服务端微信回调或主动查单结果确认支付，验签并校验金额、商户订单号和订单状态；重复通知 MUST 不得重复发放权益。

#### Scenario: Duplicate callback
- **WHEN** 同一支付成功通知到达两次
- **THEN** 系统只确认一次订单并只执行一次权益发放

#### Scenario: Unpaid timeout
- **WHEN** 订单创建超过 30 分钟仍未支付
- **THEN** 系统关闭订单且不得发放权益

