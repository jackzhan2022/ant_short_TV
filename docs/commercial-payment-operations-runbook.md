# Commercial Payment Operations Runbook

## WeChat Pay configuration

Configure `WECHAT_PAY_ENABLED=true` and provide the app ID, merchant ID, merchant certificate serial number, PKCS#8 private-key path, 32-byte API v3 key, and public HTTPS notify URL listed in `env.example`. The notify URL must end in `/api/commercial/payments/wechat/notify` and must be reachable by WeChat Pay.

The official WeChat Pay Java SDK automatically downloads, caches, selects, and refreshes platform certificates through API v3. Do not configure or deploy a static WeChat Pay platform certificate for the current application version. One SDK configuration is reused for payment requests and notifications.

Keep sales disabled during the first deployment. Trigger SDK initialization with valid merchant credentials and confirm that the platform certificate request succeeds before enabling package sales. Configuration or certificate initialization failures are fail-closed; never bypass response or notification verification.

Before enabling sales, create a low-value package version, publish it, create a Native order, verify the QR code opens in WeChat, and confirm the callback completes the order and creates exactly one point-ledger grant.

## Callback failures

Failed validations leave the order in `PAYMENT_EXCEPTION`. Inspect `commercial_payment_event.payload_json`, `commercial_payment.raw_response_json`, and `commercial_audit` entries with operation `PAYMENT_EXCEPTION`. Do not edit paid amounts or replay callbacks manually. Correct the configuration or provider evidence, then invoke `POST /api/platform/commercial/orders/{orderId}/reconcile` as a platform operator.

If automatic certificate initialization or refresh fails, verify outbound HTTPS access to `api.mch.weixin.qq.com`, the merchant ID, merchant certificate serial number, PKCS#8 private key, and the exact 32-byte API v3 key. An unknown `Wechatpay-Serial`, invalid signature, or decrypt failure must remain rejected. The SDK retains managed valid certificates during normal rotation; no manual platform certificate replacement is required.

Duplicate notifications are expected. The `(provider, provider_event_id)` unique key and order/grant idempotency keys prevent duplicate fulfillment.

## Rollback

Rolling back to an application version that predates automatic certificate management also requires restoring that version's `WECHAT_PAY_PLATFORM_CERTIFICATE_PATH` and matching platform certificate file. Keep the old file available until the new version has completed a low-value payment smoke test. Do not delete or roll back commercial orders, payment events, grants, or point-ledger entries.

## Pending entitlement grants

Orders in `ENTITLEMENT_PENDING` represent confirmed payment whose entitlement fulfillment has not completed. Confirm the package version and entitlement snapshot still exist, review the latest grant error, and retry fulfillment through the application service or deployment recovery job. Never add balance directly in the database.

## Queued subscriptions

Different subscription packages purchased during an active subscription remain `QUEUED`. Their time windows begin at the current queue tail. The periodic scheduler promotes due subscriptions and writes the first-period grant. When a queue appears stuck, compare `starts_at` with application/database time and run the due-subscription job after resolving clock or scheduler configuration issues.

## Reconciliation

For a tenant, compare `team_point_account` with `point_ledger` using `GET /api/tenants/{tenantId}/points/reconciliation`. A healthy result has `matches=true`. Trace commercial grants through `commercial_entitlement_grant.idempotency_key` to the same key in `point_ledger`; trace payment evidence through order, payment, notification event, and audit records. Preserve all records and apply corrective ledger entries through supported accounting services only. Refunds are outside the current product scope.
