## ADDED Requirements

### Requirement: Extract an audio selection from a project video result
The system SHALL allow authorized users to open audio extraction from a storyboard video result, choose start and end times, and preview a generated WAV containing that selection. The interface SHALL state that background sounds are preserved.

#### Scenario: Preview a valid selection
- **WHEN** a user selects a valid range within a decodable video audio track
- **THEN** the system produces a playable WAV for that range and enables saving after a non-empty name is provided

#### Scenario: Invalid or unavailable audio
- **WHEN** the source has no decodable audio, exceeds the input limit, or the range is invalid or longer than 30 seconds
- **THEN** the system displays a clear error and prevents saving invalid output

### Requirement: Compact extraction layout and accessible waveform selection
The system SHALL use the selected A design with fixed header/footer, source project and video context, a real audio waveform, numeric range inputs, and a persistent result preview area. Range handles SHALL support keyboard operation and have accessible names. Original video and extracted audio SHALL play mutually exclusively.

#### Scenario: Adjust the selection without dragging
- **WHEN** a user changes a waveform handle with the keyboard or edits the start/end fields
- **THEN** the selection updates and any previous extracted preview is invalidated

#### Scenario: Preview the selected source segment
- **WHEN** the user plays the original video
- **THEN** playback starts within the selection and pauses at its end without overlapping extracted audio playback

#### Scenario: Retry source reading
- **WHEN** reading fails or exceeds 45 seconds
- **THEN** an inline error and retry action are available without permitting invalid audio to be saved

### Requirement: Save extracted audio to the current project library
The system SHALL save a named extracted audio clip through the existing project upload and audio registration APIs. The result SHALL be available to the existing audio library and character voice picker.

#### Scenario: Save and reuse
- **WHEN** the user saves a valid preview with a name
- **THEN** an active project audio asset is registered under the current project and a success state is displayed

#### Scenario: Open the destination library
- **WHEN** the user chooses to view audio after saving
- **THEN** the dialog closes and navigates to the source project's audio library

#### Scenario: Retry a failed registration
- **WHEN** audio upload succeeds but audio registration fails and the user retries without changing the selection
- **THEN** the system reuses the completed upload session instead of uploading again

### Requirement: Respect authorization and extraction lifecycle
The system SHALL gate the entry point by source-view and project-audio permissions, prevent duplicate saves, and release resources and ignore stale work after closing or changing project, tenant, or video.

#### Scenario: Change selection or close
- **WHEN** the user changes the selection or closes the extraction dialog
- **THEN** obsolete preview URLs and pending work are released and cannot replace the current selection or save into a new context

#### Scenario: Insufficient permission
- **WHEN** the user lacks source video view or project audio edit/view permission
- **THEN** the extraction entry point is unavailable and the existing API authorization remains authoritative
