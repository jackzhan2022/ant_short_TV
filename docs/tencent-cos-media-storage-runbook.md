# Tencent COS Media Storage Runbook

## Runtime Resources

- Bucket: `antv-1418200553`
- Region: `ap-guangzhou`
- Access: private read/write
- Storage class: intelligent tiering
- CDN: `antvcdn.aixmax.cn`
- CDN authentication: Type D

Pre-release production and test deployments intentionally share these resources. Provision separate test resources before post-launch testing could affect live customer media.

## Required CAM Scope

Attach a least-privilege instance role to the backend. It needs object read/write/metadata and multipart operations for the application prefixes, Cloud Infinite processing operations, and STS federation-token issuance. For asynchronous image processing include `ci:CreateMediaJobs`, `ci:DescribePicProcessQueues`, and the narrowly scoped `cam:PassRole` permission required by Tencent's job API. Do not place permanent SecretId or SecretKey values in application configuration.

Browser STS policies are generated per upload object and allow only `PutObject`, multipart initiate/upload/list/complete/abort, and `HeadObject`. They do not allow reads, deletes, or bucket listing.

## COS Configuration

- Abort incomplete multipart uploads after 3 days.
- Remove unconfirmed staging uploads after 7 days and failed intermediates after 14 days.
- Keep durable originals and published derivatives.
- Keep versioning, cross-region replication, and global acceleration disabled.
- Allow CORS only from deployed frontend origins and expose ETag-related headers required by the COS browser SDK.

## CDN Configuration

- Private COS origin authorization and HTTPS enabled.
- Node cache: 30 days.
- Browser cache: 7 days.
- Cache key ignores Type D `sign` and `t` only; media-processing parameters remain significant.
- Cache auto-refresh disabled and coalesced origin requests enabled.
- Range origin requests enabled for durable video extensions only.

## Cloud Infinite Configuration

Each image original produces one persistent intelligent-compression `display.webp`; thumbnail and preview roles share that object. Derived paths must not retrigger processing. `CreateMediaJobs` selects the active picture-processing queue automatically; use `DescribePicProcessQueues` for readiness checks rather than configuring a queue ID in the application. Each job supplies its own JSON callback URL containing a single-purpose random token. Persist only the token hash and validate the callback's job ID, input key, output key, and `UserData` correlation before changing state. Coverless videos use a one-second snapshot with a first-decodable-frame fallback; AI videos and episode compositions reuse their bound first frame where available.

## Release Checks

Verify correct, expired, and tampered Type D URLs; browser upload renewal; cross-tenant denial; CDN HIT behavior across signature changes; video `206 Partial Content`; lifecycle targeting; Cloud Infinite outputs; cost alerts; and absence of media bodies on the application server's public link.
