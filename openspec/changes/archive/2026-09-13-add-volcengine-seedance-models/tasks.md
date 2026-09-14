## 1. Built-in Model Catalogue

- [x] 1.1 Inspect existing provider/model migrations and add an additive migration that seeds the disabled `VOLCENGINE_ARK` Provider with the canonical Ark base URL.
- [x] 1.2 Seed disabled, non-default `SEEDANCE_2_0_FAST`, `SEEDANCE_2_0_STANDARD`, and `SEEDANCE_2_5` Models with stable source-owned Endpoint-ID placeholders and `VIDEO_GENERATION` capabilities.
- [x] 1.3 Add migration/schema coverage proving the Provider and all three models/capabilities are present, disabled, non-default, and independently identifiable.

## 2. Ark Provider Transport

- [x] 2.1 Add a dedicated Seedance Ark adapter that validates credentials and rejects unresolved Endpoint-ID placeholders before HTTP contact.
- [x] 2.2 Implement Ark asynchronous submission by mapping existing video task inputs to text/image content and supported generation options, then parsing provider request and external task IDs.
- [x] 2.3 Implement Ark task polling, mapping transient and terminal statuses plus video result URLs and provider error details to the existing provider execution outcome contract.
- [x] 2.4 Register the adapter in model routing/video execution so only the Volcengine Ark Provider uses the native Ark request and polling behavior; retain all existing provider behavior.

## 3. Durable Video Lifecycle Integration

- [x] 3.1 Route Seedance video tasks through the existing invocation, execution attempt, call-log, pricing, point-settlement, and polling lifecycle without changing public video-task APIs.
- [x] 3.2 Verify successful Seedance polling sends the transient provider result URL through the existing project-owned result-storage flow.
- [x] 3.3 Verify failed or cancelled Seedance tasks retain provider IDs and normalized diagnostic details while completing the domain task and settlement lifecycle correctly.

## 4. Verification

- [x] 4.1 Add adapter HTTP tests for text-to-video and image-to-video payloads, accepted submissions, running polls, successful result extraction, terminal provider failures, and error normalization.
- [x] 4.2 Add routing/execution integration tests proving each enabled Seedance variant resolves independently and that disabled or placeholder models make no provider HTTP request.
- [x] 4.3 Run targeted backend tests, Flyway/schema migration tests, and backend compilation; record any live-endpoint validation deferred until the user supplies the three Endpoint IDs.
