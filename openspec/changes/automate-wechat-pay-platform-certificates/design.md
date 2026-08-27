## Context

现有商业化支付适配层使用 Java `HttpClient` 手工构造微信支付 API v3 请求，并从 `WECHAT_PAY_PLATFORM_CERTIFICATE_PATH` 加载单个平台证书完成响应和回调验签。该实现要求运维人员跟踪证书轮换，且单证书模型在微信更换回调签名证书时存在中断风险。

本变更参考微信支付官方《使用 Java SDK》文档，采用 `com.github.wechatpay-apiv3:wechatpay-java:0.2.17` 和 `RSAAutoCertificateConfig`。支付订单、通知证据、状态机、对账、幂等以及权益发放均保持现有业务边界。

## Goals / Non-Goals

**Goals:**

- 自动下载、缓存、按序列号选择并轮换微信支付平台证书。
- 使用一个应用级 SDK `Config` 实例统一处理请求签名、响应验签、通知验签和通知资源解密。
- 保持现有 `WechatPayClient` 业务接口以及支付后的校验、审计和权益发放行为。
- 配置或信任初始化失败时拒绝支付，不允许绕过验签。
- 微信支付关闭时不创建自动证书配置，也不访问微信接口。

**Non-Goals:**

- 新增 JSAPI、小程序内支付或其他支付渠道。
- 新增退款能力。
- 修改订单、订阅、积分账本或权益规则。
- 同时维护手工平台证书和自动证书两套信任源。

## Decisions

### 使用官方 Java SDK 管理完整协议层

引入 `wechatpay-java:0.2.17`，由 SDK 承担商户请求签名、响应验签、平台证书管理和通知解析。相比只用 SDK 下载证书但保留自写验签，该方案避免两套加密协议实现并存，也能跟随微信支付对证书轮换和协议细节的维护。

完全自行实现 `/v3/certificates` 被否决，因为首次获取、并发刷新、按序列号选择、旧证书有效期和失败恢复都属于高风险安全逻辑。

### 将 RSAAutoCertificateConfig 注册为条件单例

当 `commercial.wechat.enabled=true` 时，Spring 使用商户号、商户证书序列号、PKCS#8 私钥路径和 32 字节 API v3 Key 构建唯一的 `RSAAutoCertificateConfig`。支付服务和 `NotificationParser` 共享该实例，符合官方文档关于避免重复下载证书的要求。

当微信支付关闭时不创建该配置，确保普通开发、测试和非支付部署不会触发远程证书请求。

### 保留业务适配边界

`WechatPayV3Client` 继续实现 `WechatPayClient`，内部改用 SDK `NativePayService` 完成 Native 下单、商户订单号查单和关单。SDK模型被映射为现有 `WechatNativeOrder` 与 `WechatPaymentStatus`，上层订单生命周期服务无需感知 SDK。

### 使用 NotificationParser 后继续业务校验

通知控制器继续传入原始请求体和四个微信签名请求头。`NotificationParser` 必须先完成平台证书选择、签名验证和 AES-GCM 解密，应用才可以校验交易状态、商户号和 AppID并进入现有金额、币种、订单状态和幂等处理。

### 移除静态平台证书配置

删除 `WechatPayProperties`、`application.yml`、`env.example` 和支付运维说明中的 `WECHAT_PAY_PLATFORM_CERTIFICATE_PATH`。不保留静态证书兜底，避免自动配置失败时意外切换到过期或错误证书。

## Risks / Trade-offs

- [SDK 配置构造会访问微信平台证书接口] → 仅在支付启用时初始化，部署时先关闭售卖并验证凭据和网络。
- [SDK 升级可能改变模型或异常类型] → 将 SDK 封装在现有支付适配层，业务服务只依赖项目内记录类型。
- [API v3 Key、私钥或序列号错误导致初始化失败] → 在构建 SDK 前执行缺项、32 字节密钥和私钥文件校验，输出不包含敏感值的错误。
- [证书刷新期间微信接口暂时不可达] → 使用 SDK 内置缓存和刷新策略，绝不关闭验签；无法建立可信结果时支付请求或通知失败关闭。
- [删除静态证书配置降低旧版本兼容性] → 部署时保留旧证书文件直至新版本验证完成，回滚旧版本时恢复对应环境变量。

## Migration Plan

1. 增加官方 SDK 依赖和条件单例配置，保持微信支付默认关闭。
2. 迁移 Native 下单、查单、关单以及回调解析，并完成自动化回归。
3. 删除运行时静态平台证书配置和相关自写验签器。
4. 在测试环境配置有效商户凭据，确认启动时可获取平台证书。
5. 发布低金额套餐，完成一次扫码支付，确认订单完成且仅产生一条权益发放记录。
6. 稳定后移除部署环境中的静态平台证书变量和旧文件。

回滚时恢复旧应用版本、`WECHAT_PAY_PLATFORM_CERTIFICATE_PATH` 和原平台证书文件。数据库订单、支付事件、审计、权益和积分流水不作回滚或删除。

## Open Questions

无。SDK版本、信任模式、配置生命周期和静态证书迁移方式均已确定。
