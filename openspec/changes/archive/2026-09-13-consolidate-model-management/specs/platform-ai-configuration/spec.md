## ADDED Requirements

### Requirement: Platform configuration labels align with model management
The platform AI configuration interface SHALL present Provider configuration as "模型服务商" and Model configuration as "AI 大模型" when these capabilities are accessed through model management. This naming change SHALL not alter platform-only configuration authority or the existing Provider and Model permission model.

#### Scenario: Authorized platform administrator manages configuration
- **WHEN** an authorized platform administrator enters model management and opens either configuration tab
- **THEN** the system SHALL retain the existing permitted configuration operations while using the model-management labels
