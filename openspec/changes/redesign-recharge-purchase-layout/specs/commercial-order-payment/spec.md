## MODIFIED Requirements

### Requirement: WeChat native order
系统 SHALL 为团队管理员创建微信 Native 扫码订单，保存团队、套餐版本、价格快照和唯一商户订单号。商品卡片选择 SHALL 仅更新选购摘要；只有具备权限的管理员明确确认付款时，客户端 SHALL 提交当前有效的可售套餐版本创建订单。客户端 MUST 在提交期间阻止重复确认，且 MUST NOT 依据客户端选中状态或二维码展示推断支付成功。

#### Scenario: Create order
- **WHEN** 有权限的团队管理员选择可售套餐并主动确认付款
- **THEN** 系统创建待支付订单并返回微信支付二维码参数
- **AND** 付款界面使用服务端返回的金额、币种和有效期

#### Scenario: Select without confirming payment
- **WHEN** 用户切换会员周期、商品类别或选中卡片，但未确认付款
- **THEN** 客户端仅更新选购摘要，不调用创建订单接口

#### Scenario: Repeat confirmation while submitting
- **WHEN** 同一次创建订单请求尚未返回，用户再次点击确认付款
- **THEN** 客户端维持提交中状态且不再次发送创建订单请求

#### Scenario: Create order fails or returns no payment code
- **WHEN** 服务器拒绝创建订单、请求失败或响应中没有可用二维码参数
- **THEN** 页面显示具体错误或支付服务不可用说明，恢复适当的可操作状态
- **AND** 不展示假付款码、不自动重发创建请求，也不提示支付成功
- **AND** 网络结果不确定时可以读取待支付订单，用户可以显式继续支付已存在的订单

#### Scenario: Confirm an unavailable offer
- **WHEN** 确认付款时服务器判定所选版本已下架、尚未生效或已超过生效截止时间
- **THEN** 页面显示拒绝原因并刷新可售目录
- **AND** 不自动以另一件商品替换并下单

#### Scenario: Mixed membership grants its one-time points
- **WHEN** 已付款的会员同时包含周期积分与一次性积分
- **THEN** 一次性积分按该购买订单发放一次，周期积分按会员规则发放
- **AND** 排队会员购买与同套餐续费也按各自订单获得一次性积分，重试不重复入账
- **AND** 零额度积分不产生积分账本入账，也不阻塞付款订单完成

#### Scenario: Payment reaches its QR expiry while confirmation is in flight
- **WHEN** 本地二维码到期而服务器尚未返回最终支付状态
- **THEN** 客户端先查单，不能只依据本地时间断言付款失败
- **AND** 过期二维码停止展示，订单继续由服务端状态核对直至终态
- **AND** 查单暂时失败时也不继续展示已过期二维码

#### Scenario: Paid order awaits entitlement fulfillment
- **WHEN** 查单返回 `ENTITLEMENT_PENDING`
- **THEN** 客户端说明付款已确认但权益仍在发放，停止展示二维码并继续查单
- **AND** 仅在服务器返回 `COMPLETED` 后提示权益到账并刷新账户数据

#### Scenario: Point package contains membership-only entitlements
- **WHEN** 积分包草稿或待发布版本包含周期积分或会员全局折扣
- **THEN** 后端拒绝该组合；历史异常版本不进入可售目录且不能创建付款订单
- **AND** 客户端拿到旧目录数据时也不展示不能兑现的权益，并禁用该商品的付款确认
