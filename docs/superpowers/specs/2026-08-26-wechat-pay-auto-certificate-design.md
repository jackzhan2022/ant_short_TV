# WeChat Pay Platform Certificate Auto-Update Design

## Context

The commercial payment integration currently implements WeChat Pay API v3 with Java `HttpClient` and loads one WeChat Pay platform certificate from `WECHAT_PAY_PLATFORM_CERTIFICATE_PATH`. This requires manual certificate installation and cannot automatically follow platform certificate rotation.

The implementation will follow the official WeChat Pay Java SDK guide at <https://pay.weixin.qq.com/doc/v3/merchant/4012076506>. The guide uses `RSAAutoCertificateConfig` as a singleton to download and update platform certificates.

## Goals

- Use the official WeChat Pay Java SDK to download, cache, select, and update platform certificates.
- Use one application-wide SDK configuration instance to avoid duplicate certificate downloads.
- Let the SDK handle merchant request signing, response verification, notification signature verification, and notification resource decryption.
- Preserve the existing order lifecycle, payment evidence, idempotency, reconciliation, and entitlement behavior.
- Fail closed when credentials are incomplete or platform certificate initialization fails.

## Non-Goals

- Adding JSAPI or Mini Program payment.
- Adding refunds.
- Changing commercial package, order, subscription, or entitlement rules.
- Supporting an unsigned or verification-disabled fallback.
- Maintaining a second static platform-certificate trust source.

## Dependencies And Configuration

Add Maven dependency `com.github.wechatpay-apiv3:wechatpay-java:0.2.17`.

Create one Spring-managed `RSAAutoCertificateConfig` from:

- `WECHAT_PAY_MERCHANT_ID`
- `WECHAT_PAY_MERCHANT_SERIAL_NUMBER`
- `WECHAT_PAY_MERCHANT_PRIVATE_KEY_PATH`
- `WECHAT_PAY_API_V3_KEY`

The API v3 key must contain exactly 32 UTF-8 bytes. The merchant private key must remain a PKCS#8 PEM file.

Remove the runtime dependency on `WECHAT_PAY_PLATFORM_CERTIFICATE_PATH` from application configuration, the local environment template, and the payment operations runbook. Existing local values may remain harmless during deployment rollout but will no longer be read.

## Architecture

### SDK Configuration

A payment SDK configuration component will construct `RSAAutoCertificateConfig` once when WeChat Pay is enabled. It will validate required configuration before invoking the SDK builder. The resulting `Config` instance will be shared by all WeChat Pay services and notification parsing.

When WeChat Pay is disabled, ordinary application startup and non-payment tests must not contact WeChat Pay. Payment calls remain unavailable through the existing `commercial.wechat.enabled` behavior.

### Payment Client

`WechatPayV3Client` will continue implementing the existing `WechatPayClient` boundary so commercial services remain unchanged. Internally it will use the SDK `NativePayService` for:

- Native prepay and `code_url` generation.
- Querying an order by merchant order number.
- Closing an unpaid order.

The adapter will map SDK request and response models to the existing records. SDK exceptions will be wrapped in the existing application-level payment exceptions without exposing private keys or response secrets.

### Notification Verification

`WechatPayNotificationVerifier` will use one SDK `NotificationParser` created from the shared configuration. It will pass the raw callback body and all WeChat signature headers to the SDK. After successful SDK parsing, the application will retain its own semantic checks:

- Transaction state is `SUCCESS`.
- Callback merchant ID equals configured merchant ID.
- Callback AppID equals configured AppID.
- Merchant order number, amount, currency, and order state match persisted data.
- Duplicate provider event IDs do not duplicate payment confirmation or entitlement grants.

No callback content may reach payment confirmation before SDK verification and decryption succeed.

### Legacy Verifier Removal

The custom platform-certificate response verifier becomes redundant because the SDK verifies API responses. It will be removed with its direct unit test. Cryptographic behavior will instead be covered at the SDK adapter boundary and by the SDK's maintained implementation.

## Data Flow

1. Spring creates the shared SDK configuration when WeChat Pay is enabled.
2. The SDK signs a `/v3/certificates` request with the merchant private key, decrypts the returned certificates with the API v3 key, and manages certificate rotation.
3. A commercial order calls the existing `WechatPayClient` interface.
4. The SDK signs the Native payment request and verifies the response with the managed platform certificates.
5. WeChat Pay posts a callback to `/api/commercial/payments/wechat/notify`.
6. `NotificationParser` selects the platform certificate by `Wechatpay-Serial`, verifies the signature, and decrypts the transaction.
7. Existing business validation, idempotent payment confirmation, and entitlement fulfillment run unchanged.

## Failure Handling

- Missing merchant ID, merchant serial number, private-key path, or API v3 key: fail with an explicit configuration error before a payment request is sent.
- API v3 key not exactly 32 bytes: fail configuration validation.
- Platform certificate download or update failure: rely on SDK behavior; never bypass verification. Initialization failure prevents payment capability from becoming ready.
- Unknown or invalid callback certificate/signature: reject the callback and do not change order state.
- Invalid callback merchant ID, AppID, amount, currency, or state: preserve the existing payment-exception evidence behavior.
- Interrupted SDK or network operation: preserve thread interruption where applicable and return an application-level payment failure.

## Testing

Development follows red-green-refactor:

1. Add failing configuration tests for required values, 32-byte API v3 key validation, singleton SDK configuration, and disabled-mode behavior.
2. Add failing payment-adapter tests for Native prepay, query, close, and SDK error mapping using an injected SDK-facing boundary.
3. Add failing notification tests proving parsed transactions still enforce `SUCCESS`, merchant ID, and AppID.
4. Implement the minimum SDK integration needed to pass each test.
5. Run existing commercial lifecycle and controller tests to verify business behavior remains unchanged.
6. Run a real low-value Native payment smoke test only after valid merchant credentials and a resolvable public HTTPS callback are available.

## Rollout

Deploy first with sales disabled and valid merchant credentials configured. Start the application and verify SDK certificate initialization. Enable sales, publish a low-value test package, complete one Native payment, and verify exactly one payment event and entitlement grant. Remove obsolete platform certificate files only after the deployment is stable.

Rollback restores the previous application version and its platform certificate path. Existing orders, payments, events, grants, and ledger entries are preserved.
