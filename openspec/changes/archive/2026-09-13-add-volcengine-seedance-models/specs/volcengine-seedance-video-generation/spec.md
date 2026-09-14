## ADDED Requirements

### Requirement: Seedance variants are independently routable video-generation models
The system SHALL define Seedance 2.0 Fast, Seedance 2.0 Standard, and Seedance 2.5 as separate platform Models under one Volcengine Ark Provider. Each Model SHALL support `VIDEO_GENERATION` and retain an independent stable code, model identity, pricing identity, and call-log identity.

#### Scenario: A selected Seedance variant starts a video task
- **WHEN** a project submits an authorized video-generation task with an enabled Seedance 2.0 Fast, Seedance 2.0 Standard, or Seedance 2.5 model ID
- **THEN** the system resolves that exact Model and uses its own Ark Endpoint ID for provider submission

#### Scenario: Seedance variants are priced independently
- **WHEN** a platform administrator assigns prices to two different Seedance variants
- **THEN** each variant's price versions apply only to tasks resolved to that Model

### Requirement: Seedance uses native Ark asynchronous task operations
The system SHALL submit Seedance video-generation requests to Volcengine Ark as provider-native asynchronous tasks and SHALL poll the returned external task identifier until a terminal provider state is observed.

#### Scenario: Ark accepts a generation task
- **WHEN** the Seedance adapter receives a valid video-generation task with configured credentials and a valid Endpoint ID
- **THEN** it sends the Ark request, records the provider request ID and external task ID, and leaves the application task non-terminal for polling

#### Scenario: Ark completes a generation task
- **WHEN** polling returns a successful terminal Ark task with a video result URL
- **THEN** the system records provider completion and passes the result URL to the existing project-owned result-storage flow

#### Scenario: Ark reports a failed generation task
- **WHEN** polling returns a failed or cancelled terminal Ark task
- **THEN** the system marks the video task failed with normalized diagnostics and retains the provider request/task linkage in execution and call-log records

### Requirement: Seedance request input maps from the existing video task contract
The Seedance adapter SHALL construct its request solely from the resolved Model and existing video task inputs. It SHALL include the selected Endpoint ID, prompt, configured duration/ratio/resolution, and the first-frame image URL when the task is image-to-video.

#### Scenario: Text-to-video task is submitted
- **WHEN** a video task has no first-frame image URL
- **THEN** the Ark request contains the text prompt and configured generation options without an image content item

#### Scenario: Image-to-video task is submitted
- **WHEN** a video task has a valid first-frame image URL
- **THEN** the Ark request contains both the text prompt and the image URL content item

### Requirement: Unconfigured Seedance definitions fail closed
The three Seedance Models SHALL be disabled by default and SHALL not be selected as default Models. The adapter SHALL reject a blank or placeholder Endpoint ID before contacting Ark.

#### Scenario: User selects a disabled Seedance model
- **WHEN** a project requests video generation with a disabled Seedance Model
- **THEN** model routing rejects the request before provider contact

#### Scenario: Placeholder Endpoint ID reaches the adapter
- **WHEN** an enabled Seedance Model still contains an unresolved Endpoint-ID placeholder
- **THEN** the adapter returns a normalized configuration error without sending an HTTP request
