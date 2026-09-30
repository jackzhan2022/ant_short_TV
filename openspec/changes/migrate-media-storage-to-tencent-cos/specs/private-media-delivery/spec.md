## ADDED Requirements

### Requirement: Private browser media requires resource authorization
The system SHALL verify the caller's current tenant, project, and resource permission before issuing a browser media URL. Authorized browser delivery SHALL use the configured private CDN domain with Type D authentication; anonymous COS URLs and public-read buckets MUST NOT be used.

#### Scenario: Owner requests an asset rendition
- **WHEN** an authenticated caller with access to an asset version requests an available rendition
- **THEN** the backend returns a Type D signed URL for that exact immutable object key

#### Scenario: Caller requests another user's asset
- **WHEN** a caller lacks permission for the requested resource or version
- **THEN** the backend returns not found or forbidden according to the resource contract
- **AND** does not create or disclose a signed URL

### Requirement: Delivery grants persist stable seven-day authorization periods
The system SHALL persist a seven-day delivery authorization period per caller and immutable resource rendition and SHALL derive the same signed URL throughout that period without persisting the full URL or signature. Renewal SHALL be concurrency-safe, and a video URL with less than two hours remaining SHALL be renewed before presentation.

#### Scenario: Asset is requested repeatedly within one period
- **WHEN** the same authorized caller requests the same immutable rendition more than once before its stored grant expires
- **THEN** the backend derives and returns the same complete CDN URL
- **AND** does not extend the authorization period on every request

#### Scenario: Concurrent requests renew an expired grant
- **WHEN** concurrent authorized requests observe an expired delivery grant
- **THEN** one transactional authorization period becomes authoritative
- **AND** all successful responses derive their URL from that period

#### Scenario: Video grant nears expiration
- **WHEN** an authorized video request has less than two hours of validity remaining
- **THEN** the backend renews the grant before returning a playback URL

### Requirement: CDN and browser caching preserve authorization-independent object identity
The deployment SHALL cache immutable media at CDN nodes for 30 days and direct browsers to cache eligible media for seven days. CDN cache keys SHALL exclude only Type D `sign` and `t` authentication parameters while retaining Cloud Infinite and other representation parameters. Automatic cache refresh SHALL remain disabled, and content updates SHALL use new object keys.

#### Scenario: Signature changes for one object
- **WHEN** an immutable object's Type D authorization period changes but its object key and representation remain the same
- **THEN** the request resolves to the existing CDN cache object instead of a signature-specific cache entry

#### Scenario: Image representation changes
- **WHEN** two requests identify different media-processing representations of one original
- **THEN** CDN cache keys keep those representations distinct

#### Scenario: Browser reuses an unexpired media response
- **WHEN** a browser requests the same complete signed URL again within its seven-day cache freshness period
- **THEN** it can satisfy the request without another CDN body transfer

### Requirement: Video delivery supports efficient range access
The CDN configuration SHALL keep coalesced origin requests enabled and SHALL enable range origin requests only for durable video representations. Video delivery SHALL support byte-range seeking without fetching the complete source when only a range is required.

#### Scenario: User seeks within a cached or partially cached video
- **WHEN** the browser sends a valid byte-range request for a durable video object
- **THEN** delivery returns `206 Partial Content` with valid range headers
- **AND** avoids an unnecessary full-object COS origin transfer

### Requirement: External AI access uses separate controlled COS URLs
The system SHALL issue external AI providers a COS presigned URL scoped to the required object and valid for the task timeout plus 30 minutes. Model-access URLs SHALL not be stored as business resource identities or returned as browser delivery URLs.

#### Scenario: Video understanding task needs a source object
- **WHEN** an authorized task prepares a COS-stored source for an external model
- **THEN** it receives a task-bounded COS URL that remains valid through the configured execution window
- **AND** the URL reveals no application or cloud credential

### Requirement: Delivery secrets and complete signed URLs are excluded from logs
The system MUST NOT log Type D keys, complete signed CDN URLs, COS instance-role credentials, COS authorization headers, security tokens, or model-access URL query strings. Operational events SHALL identify resources through internal IDs, redacted keys, and request identifiers.

#### Scenario: Delivery or upload request fails
- **WHEN** an error is recorded for URL signing, COS access, CDN delivery, or STS issuance
- **THEN** diagnostics retain actionable resource and correlation identifiers
- **AND** redact credentials and signed query values
