# wechat-pay-platform-trust Specification

## Purpose
TBD - created by archiving change automate-wechat-pay-platform-certificates. Update Purpose after archive.
## Requirements
### Requirement: Automatic WeChat Pay platform certificate management
系统 SHALL 在微信支付启用时使用微信支付官方 Java SDK 和全局单例的自动证书配置，通过 API v3 获取、缓存、按序列号选择并更新微信支付平台证书；系统 MUST NOT 依赖本地静态平台证书文件作为运行时信任源。

#### Scenario: Initialize automatic certificate management
- **WHEN** 微信支付已启用且商户号、商户证书序列号、PKCS#8 商户私钥和 32 字节 API v3 Key 均有效
- **THEN** 系统创建唯一的自动证书配置供所有微信支付请求和通知验签复用

#### Scenario: Rotate platform certificate
- **WHEN** 微信支付平台使用自动证书配置管理的新有效证书序列号签名响应或通知
- **THEN** 系统按该序列号选择可信证书完成验签，无需运维人员更新本地证书文件

### Requirement: Unified verified payment protocol
系统 SHALL 使用共享的官方 SDK 配置完成 Native 下单、查单和关单的请求签名与响应验签，并 MUST 在支付通知进入业务确认前使用同一信任配置完成通知签名验证和资源解密。

#### Scenario: Create a Native payment order
- **WHEN** 团队管理员为有效套餐创建微信 Native 支付订单
- **THEN** 系统通过官方 SDK 签名请求、验证微信响应并返回 `code_url`

#### Scenario: Process a valid payment notification
- **WHEN** 微信支付发送由当前可信平台证书签名且可用 API v3 Key 解密的成功交易通知
- **THEN** 系统在 SDK 验签和解密成功后继续校验交易状态、商户号、AppID、订单、金额和币种

#### Scenario: Reject an invalid payment notification
- **WHEN** 支付通知签名无效、证书序列号不受信任或密文无法解密
- **THEN** 系统拒绝该通知且 MUST NOT 确认支付或发放权益

### Requirement: Fail-closed payment configuration
系统 MUST 在微信支付配置缺失、API v3 Key 不是 32 字节、商户私钥无效或自动证书初始化失败时拒绝支付能力，且 MUST NOT 通过关闭验签或回退到不可信证书继续处理。

#### Scenario: Reject incomplete credentials
- **WHEN** 微信支付已启用但自动证书配置所需的任一商户凭据缺失或格式无效
- **THEN** 系统以不包含密钥内容的明确配置错误拒绝初始化或支付请求

#### Scenario: Reject certificate initialization failure
- **WHEN** 系统无法通过官方 SDK 建立可信的平台证书配置
- **THEN** 系统拒绝支付请求和无法验签的通知，不改变订单支付状态

### Requirement: Disabled payment isolation
系统 SHALL 在微信支付关闭时跳过官方 SDK 自动证书配置初始化和远程平台证书请求，使非支付功能和自动化测试不依赖微信支付网络或凭据。

#### Scenario: Start with WeChat Pay disabled
- **WHEN** `commercial.wechat.enabled=false`
- **THEN** 应用可以在不提供微信商户凭据且不请求微信平台证书接口的情况下启动

