## ADDED Requirements

### Requirement: Runtime media storage uses private Tencent COS exclusively
The system SHALL store all newly generated or uploaded durable media in the configured private Tencent COS bucket and SHALL access COS from the backend with a CAM-associated instance role and the configured regional internal route. Runtime media operations MUST NOT read from, write to, or fall back to MinIO or local filesystem storage.

#### Scenario: Backend persists generated media
- **WHEN** a backend AI or composition workflow produces a durable media result
- **THEN** the system streams that result to the configured COS bucket using the backend CAM role
- **AND** does not consume application-server public egress bandwidth for the COS upload

#### Scenario: COS write fails
- **WHEN** COS rejects or cannot complete a durable media write
- **THEN** the owning workflow records a retryable or terminal storage failure according to its existing execution policy
- **AND** the system does not write the result to MinIO or local storage

#### Scenario: Historical object exists only in MinIO
- **WHEN** a caller requests an object key that was never copied to COS
- **THEN** the new storage implementation reports the object unavailable
- **AND** does not query MinIO

### Requirement: Durable objects use immutable owned keys
The system SHALL assign every new durable object a normalized key without a leading slash whose namespace identifies its tenant, optional project, asset type, asset identity, and immutable version identity. Business records SHALL persist object keys and verified object metadata rather than COS, CDN, provider, or signed URLs.

#### Scenario: Project asset version is created
- **WHEN** the system persists a new version of a project-owned asset
- **THEN** every original and derived object is stored below that tenant, project, asset, and version namespace
- **AND** updating the asset creates a new version key instead of overwriting the prior immutable key

#### Scenario: Tenant-level decomposition upload is created
- **WHEN** a tenant member uploads an unbound decomposition video
- **THEN** the system assigns a tenant-scoped upload and durable key without fabricating a project identifier
- **AND** another tenant cannot reference that key

### Requirement: Browser uploads use constrained renewable STS sessions
The system SHALL create authenticated upload sessions that return COS STS credentials valid for 60 minutes and constrained to the session's tenant/project upload prefix and required multipart actions. The frontend SHALL upload bytes directly to COS with `cos-js-sdk-v5` and SHALL be able to renew credentials during a long-running upload. The application SHALL NOT impose a business file-size ceiling, while COS platform limits and supported media validation remain enforceable.

#### Scenario: Authorized user starts a browser upload
- **WHEN** an authorized user requests an upload session for a supported media type
- **THEN** the backend allocates a unique staging key and returns temporary credentials limited to that upload prefix
- **AND** the media bytes do not traverse the Spring upload endpoint

#### Scenario: Upload outlives its initial credentials
- **WHEN** a valid multipart upload is still running as its STS credentials approach expiration
- **THEN** the frontend requests renewed credentials for the same authorized upload session
- **AND** resumes the multipart upload without receiving permanent cloud credentials

#### Scenario: Credential is used outside its prefix
- **WHEN** a browser attempts to use an upload-session credential against another tenant, project, session, or disallowed COS action
- **THEN** COS rejects the request under the CAM policy

### Requirement: Upload completion is verified before publication
The system SHALL accept a browser upload as complete only after an authorized completion request and a backend `HEAD Object` verification of the assigned key, actual length, ETag or checksum, and content type. Client-declared object metadata SHALL NOT by itself create a published material record.

#### Scenario: Uploaded object matches its session
- **WHEN** the browser completes all parts and submits the assigned key and upload evidence
- **THEN** the backend verifies the object through COS
- **AND** atomically records the durable object metadata and advances the media workflow

#### Scenario: Client submits another object key
- **WHEN** a completion request supplies an object outside the upload session namespace or with inconsistent metadata
- **THEN** the backend rejects completion and creates no material record

### Requirement: Temporary COS data is bounded by lifecycle policy
The COS environment SHALL remove incomplete multipart uploads after three days, unconfirmed staging objects after seven days, and failed-task intermediate objects after 14 days. Durable originals and published derivatives SHALL not be automatically deleted in this phase, bucket versioning and cross-region replication SHALL remain disabled, and new durable objects SHALL use intelligent tiering.

#### Scenario: Multipart upload is abandoned
- **WHEN** a multipart upload remains incomplete for three days
- **THEN** COS lifecycle processing aborts and removes its uploaded parts

#### Scenario: Durable asset remains active
- **WHEN** an original or published derivative belongs to an active asset version
- **THEN** temporary-object lifecycle rules do not delete it

### Requirement: Pre-release deployments exercise the production COS path
Before public launch, production and deployed test environments SHALL use private bucket `antv-1418200553` and CDN domain `antvcdn.aixmax.cn` so release testing exercises the exact production storage and delivery path. Unit tests SHALL use mocks or fakes. Separating the deployed test bucket and CDN SHALL remain an operational follow-up before tests could affect live customer media.

#### Scenario: Automated unit test exercises storage behavior
- **WHEN** a unit or slice test runs object-storage behavior
- **THEN** it uses a test double and performs no real COS request

#### Scenario: Pre-release deployed test environment starts
- **WHEN** the test deployment initializes before public launch
- **THEN** it resolves `antv-1418200553` and `antvcdn.aixmax.cn`
- **AND** exercises the same private storage, CDN authentication, caching, and rendition path as production

#### Scenario: Product has launched publicly
- **WHEN** deployed tests could create, replace, or delete live customer media
- **THEN** operators provision and configure separate test COS and CDN resources before continuing those tests
