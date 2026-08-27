## 1. Official SDK Configuration

- [x] 1.1 Add the official `wechatpay-java` Maven dependency and an SDK-facing adapter boundary that can be replaced in unit tests.
- [x] 1.2 Add failing tests for disabled-mode isolation, required merchant credentials, 32-byte API v3 Key validation, PKCS#8 private-key validation, and singleton configuration reuse.
- [x] 1.3 Implement a conditional Spring singleton backed by `RSAAutoCertificateConfig` and make configuration failures fail closed without logging credential values.

## 2. Native Payment Client Migration

- [x] 2.1 Add failing adapter tests for Native prepay response mapping, merchant-order query mapping, order close, and SDK exception mapping.
- [x] 2.2 Migrate `WechatPayV3Client` to the shared SDK `NativePayService` while preserving the existing `WechatPayClient` contract.
- [x] 2.3 Remove the custom API response verifier and its obsolete direct cryptographic test after SDK-backed client tests pass.

## 3. Payment Notification Migration

- [x] 3.1 Add failing tests for SDK-parsed successful notifications and rejection of invalid trade state, merchant ID, AppID, signatures, certificate serials, or encrypted resources.
- [x] 3.2 Migrate `WechatPayNotificationVerifier` to a shared-config `NotificationParser` while preserving its existing application notification record.
- [x] 3.3 Run notification service tests proving duplicate callbacks grant once and invalid payment evidence never confirms or fulfills an order.

## 4. Configuration And Operations Cleanup

- [x] 4.1 Remove `WECHAT_PAY_PLATFORM_CERTIFICATE_PATH` from runtime properties, `application.yml`, and `env.example` without changing other payment credentials.
- [x] 4.2 Update the commercial payment operations runbook with automatic certificate initialization, rotation, failure diagnosis, and rollback guidance.

## 5. Verification And Rollout

- [x] 5.1 Run the SDK configuration, Native payment adapter, notification verifier, commercial payment lifecycle, and controller permission test suites.
- [x] 5.2 Run backend compilation plus the relevant schema and commercial regression suites, then verify the diff contains no secret values or obsolete platform-certificate references.
- [x] 5.3 With valid merchant credentials and a resolvable public HTTPS callback, start with sales disabled, verify automatic certificate initialization, and complete one low-value Native payment with exactly one payment event and entitlement grant.
