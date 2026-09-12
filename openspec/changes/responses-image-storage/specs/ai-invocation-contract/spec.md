## ADDED Requirements

### Requirement: Unified invocation supports Responses image-generation tools
The unified AI invocation path SHALL submit XiongXiong image requests to `/v1/responses` using an `image_generation` tool and SHALL normalize the resulting image-generation call output as typed image data.

#### Scenario: Image generation without references
- **WHEN** a caller invokes image generation with a prompt and no reference images
- **THEN** the adapter submits a Responses request with `action` set to `generate` and returns the image-generation call result

#### Scenario: Image edit with references
- **WHEN** a caller invokes image generation with one or more reference images
- **THEN** the adapter submits each reference as an `input_image`, sets `action` to `edit`, and returns the image-generation call result

### Requirement: Responses and image models have independent configuration
The unified AI invocation path SHALL resolve the top-level Responses model from the routed `responsesModel` configuration, defaulting to `gpt-5.6-terra`, and SHALL declare the selected image-generation model at the image tool level.

#### Scenario: Route specifies a Responses model
- **WHEN** an image invocation route contains a `responsesModel` value
- **THEN** the adapter uses that value as the top-level Responses model and preserves the selected image model in the image-generation tool

#### Scenario: Route omits a Responses model
- **WHEN** an image invocation route has no `responsesModel` value
- **THEN** the adapter uses `gpt-5.6-terra` as the top-level Responses model and preserves the selected image model in the image-generation tool

### Requirement: Invalid Responses image output is normalized
The unified AI invocation path SHALL reject a successful transport response that lacks a decodable `image_generation_call` result as a normalized invalid AI response.

#### Scenario: Response has no image-generation call
- **WHEN** the Responses API returns successfully without a valid image-generation call result
- **THEN** the invocation records the failure and returns the normalized invalid-response error without creating a completed image result
