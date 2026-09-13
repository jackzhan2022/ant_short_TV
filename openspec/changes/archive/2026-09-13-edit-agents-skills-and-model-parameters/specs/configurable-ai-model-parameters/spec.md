## ADDED Requirements

### Requirement: Model text parameters are configurable
管理员 MUST 能配置模型的 temperature、top-p、最大输出 token、JSON 输出模式、超时和重试次数；后端 MUST 校验范围并拒绝非法值。

#### Scenario: Save valid model parameters
- **WHEN** an authorized administrator saves parameters within the allowed ranges
- **THEN** the system persists them and displays the active configuration version

#### Scenario: Reject invalid model parameters
- **WHEN** a request contains temperature outside 0-2, top-p outside 0-1, max tokens outside 256-32768, timeout outside 5-180 seconds, or retries outside 0-3
- **THEN** the API returns a validation error and does not change the active configuration

### Requirement: Configured parameters reach the provider
AI 文本调用 MUST 使用任务快照中的参数；启用 JSON 输出模式时，支持该能力的 OpenAI 兼容请求 MUST 包含 `response_format.type=json_object`。

#### Scenario: Structured script analysis request
- **WHEN** a script analysis task invokes a configured compatible model with JSON mode enabled
- **THEN** the outbound request contains the configured max tokens, temperature, top-p, and JSON response format

### Requirement: Invalid structured output is diagnosable
系统 MUST 记录响应长度、完成原因和是否截断，并将空响应、截断响应、非 JSON 响应和结构字段缺失映射为可区分的错误信息。

#### Scenario: Truncated JSON response
- **WHEN** a provider reports a length-limited completion or returns unclosed JSON
- **THEN** the stage fails with a truncation-specific error and marks it retryable
