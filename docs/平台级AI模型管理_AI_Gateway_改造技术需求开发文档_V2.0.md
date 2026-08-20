# 平台级 AI 模型管理 + AI Gateway 改造技术需求开发文档

> 版本：V2.0  
> 优先级：P0  
> 文档类型：技术需求开发文档  
> 改造原则：**基于现有项目能力改造，不重复建设**  
> 核心目标：将现有“租户级 AI 服务配置”升级为“平台级 Provider + Model 管理”，并建设统一 AI Gateway，后续所有 AI 业务统一通过 Gateway 调用。

---

# 1. 项目背景

当前项目已经具备较完整的 AI 服务基础能力，包括：

- AI Provider 管理
- AI Service Config
- Provider 模板
- API Key 加密
- AI 服务测试
- AI 调用日志
- AI 服务管理页面
- RBAC 权限
- 多租户体系

但当前 AI 服务配置的核心模型仍然是：

```text
租户
 ↓
AI Service Config
 ↓
Provider + API Key + Model + Endpoint
```

这种架构不符合平台化 AI 产品的长期发展方向。

本次 P0 改造后统一调整为：

```text
平台
 ↓
AI Provider
 ↓
AI Model
 ↓
AI Model Capability
 ↓
项目
 ↓
用户选择 Model
 ↓
AI Gateway
 ↓
Provider Adapter
 ↓
第三方 AI 服务
```

---

# 2. 改造目标

本次只聚焦两个核心能力：

## 2.1 平台级 AI 模型管理

平台管理员统一维护：

- AI 服务商
- API Key
- Base URL
- Model
- Model Code
- Model 能力
- 默认模型
- 模型启停
- Provider 启停

用户侧：

- 不配置 Provider
- 不配置 API Key
- 不配置 Base URL
- 不配置 Endpoint
- 不填写 Model Code
- 只能选择平台开放的 Model

---

## 2.2 AI Gateway

所有业务 AI 调用统一经过：

```text
Business Service
        ↓
    AI Gateway
        ↓
   Model Router
        ↓
Provider Adapter
        ↓
第三方 AI Provider
```

业务模块不得直接依赖：

```text
OpenAI SDK
Gemini SDK
火山 SDK
MiniMax SDK
```

---

# 3. 现有能力复用原则

本次不重新开发以下已有能力：

| 现有能力 | 处理方式 |
|---|---|
| AiProviderEntity | 保留并升级 |
| AiProviderMapper | 保留并升级 |
| AiServiceConfigEntity | 逐步废弃/迁移 |
| AiSecretCodec | 直接复用 |
| AI服务测试 | 改造为 Adapter 测试 |
| AiCallLog | 保留并扩展 |
| Provider 模板 | 保留 |
| RBAC | 保留并调整权限 |
| 多租户体系 | 保留 |
| 前端 AI 服务管理 | 改造 |
| API Key 掩码 | 保留 |

---

# 4. 当前架构问题

## 4.1 Provider 与 Model 耦合

当前：

```text
AiServiceConfig
 ├── provider
 ├── apiKey
 ├── baseUrl
 └── model
```

导致：

- 一个服务配置对应一个模型
- Model 无法独立管理
- 用户容易接触底层服务配置
- 模型上下线不够灵活
- Provider 与 Model 无法形成标准关系

---

## 4.2 AI业务与AI服务配置耦合

当前业务容易直接依赖：

```text
AiServiceConfig
```

后续容易出现：

```text
剧本模块 → AI Service Config
图片模块 → AI Service Config
视频模块 → AI Service Config
```

最终导致业务代码理解 Provider、Model、API Key 等技术概念。

改造后：

```text
业务模块
 ↓
AiGateway
```

业务模块只关心：

```text
我要调用文本模型
我要调用图片模型
我要调用视频模型
```

---

# 5. 目标架构

```text
                         ┌────────────────────┐
                         │    平台管理员       │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │    AI Provider     │
                         │ OpenAI/Gemini/火山 │
                         │ MiniMax/...        │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │      AI Model      │
                         │ GPT / Gemini / ... │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │ Model Capability   │
                         │ TEXT/IMAGE/VIDEO   │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │    Project Config  │
                         │  用户选择模型       │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │    AI Gateway      │
                         └─────────┬──────────┘
                                   │
                         ┌─────────▼─────────┐
                         │    Model Router    │
                         └─────────┬─────────┘
                                   │
                 ┌─────────────────┼─────────────────┐
                 ▼                 ▼                 ▼
          OpenAI Adapter     Gemini Adapter    Volcengine Adapter
                 │                 │                 │
                 └─────────────────┼─────────────────┘
                                   ▼
                              AI Provider
```

---

# 6. 功能范围

| 编号 | 功能 | 优先级 | 类型 |
|---|---|---|---|
| P0-01 | 平台 Provider 管理 | P0 | 改造 |
| P0-02 | 平台 Model 管理 | P0 | 新增 |
| P0-03 | Model Capability 管理 | P0 | 新增 |
| P0-04 | 默认模型管理 | P0 | 改造 |
| P0-05 | 项目模型选择 | P0 | 新增 |
| P0-06 | AI Gateway | P0 | 新增 |
| P0-07 | Model Router | P0 | 新增 |
| P0-08 | Provider Adapter | P0 | 新增 |
| P0-09 | API Key 安全 | P0 | 复用+增强 |
| P0-10 | AI 调用日志 | P0 | 改造 |
| P0-11 | AI 服务测试 | P0 | 改造 |
| P0-12 | Provider/Model权限 | P0 | 改造 |

---

# 7. Provider 管理

## 7.1 功能定位

Provider 是平台级资源。

示例：

```text
OpenAI
Gemini
火山
MiniMax
```

平台管理员负责配置。

---

## 7.2 Provider 字段

建议保留现有 `AiProviderEntity`，并规范字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| code | varchar(64) | Provider唯一编码 |
| name | varchar(128) | 服务商名称 |
| base_url | varchar(512) | 默认Base URL |
| supported_types | json | 支持服务类型 |
| default_base_url | varchar(512) | 默认地址 |
| description | varchar(1000) | 描述 |
| status | tinyint | 启用/禁用 |
| sort | int | 排序 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

---

# 8. Provider 实际连接配置

Provider 模板与实际平台配置建议分离。

## 8.1 Provider Template

用于：

```text
OpenAI
Gemini
火山
MiniMax
```

保存：

```text
Provider名称
Provider Code
默认Base URL
支持能力
推荐模型
```

---

## 8.2 Provider Config

保存平台实际使用的：

```text
API Key
Base URL
其他认证配置
```

建议新增：

```text
ai_provider_config
```

字段：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| provider_id | Provider |
| api_key_cipher | 加密API Key |
| base_url | 实际Base URL |
| extra_config | 扩展配置 |
| status | 状态 |
| last_test_status | 测试状态 |
| last_test_message | 测试信息 |
| last_test_at | 测试时间 |

如果当前 `AiProviderEntity` 已承担实际配置职责，也可以不拆表，但必须保证：

> **API Key 永远属于平台 Provider，不属于租户。**

---

# 9. Model 管理

新增：

```text
ai_model
```

## 9.1 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| provider_id | bigint | 所属Provider |
| code | varchar(128) | 平台模型编码 |
| name | varchar(128) | 展示名称 |
| model_code | varchar(256) | 第三方真实模型编码 |
| service_type | varchar(32) | TEXT/IMAGE/VIDEO/AUDIO |
| description | varchar(1000) | 描述 |
| status | tinyint | 启用/禁用 |
| is_default | tinyint | 是否默认 |
| sort | int | 排序 |
| config_json | json | 模型扩展参数 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

---

# 10. Model Code 规则

需要区分两个编码：

```text
platform model code
```

例如：

```text
GPT_TEXT_PRIMARY
```

和：

```text
provider model code
```

例如：

```text
真实第三方模型名称
```

业务模块只能使用：

```text
modelId
```

禁止直接传递第三方：

```text
model_code
```

---

# 11. Model Capability

新增：

```text
ai_model_capability
```

能力类型：

```text
TEXT_GENERATION
TEXT_REWRITE
STRUCTURED_OUTPUT

IMAGE_GENERATION
IMAGE_EDIT

VIDEO_GENERATION
VIDEO_QUERY

AUDIO_GENERATION
VOICE_CLONE
```

示例：

```text
GPT
 ├── TEXT_GENERATION
 ├── TEXT_REWRITE
 └── STRUCTURED_OUTPUT

图片模型
 ├── IMAGE_GENERATION
 └── IMAGE_EDIT
```

---

# 12. Model 启停规则

模型可用必须同时满足：

```text
Provider = ENABLED
AND
Model = ENABLED
AND
Capability = ENABLED
```

如果 Provider 被禁用：

```text
其下所有模型自动不可用
```

不要求逐条修改 Model 状态。

---

# 13. 默认模型

默认模型按照能力设置：

```text
默认文本模型
默认图片模型
默认视频模型
默认音频模型
```

建议通过：

```text
service_type
+
is_default
```

实现。

同一个服务类型：

```text
最多一个默认模型
```

数据库及 Service 层都必须保证。

---

# 14. 项目 AI 模型配置

新增：

```text
project_ai_config
```

字段：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| tenant_id | 租户 |
| project_id | 项目 |
| text_model_id | 文本模型 |
| image_model_id | 图片模型 |
| video_model_id | 视频模型 |
| audio_model_id | 音频模型 |
| created_at | 创建时间 |
| updated_at | 更新时间 |

---

# 15. 用户模型选择规则

用户进入项目后，只展示：

```text
平台启用
+
当前项目有权限
+
Provider正常
```

的模型。

用户只能看到：

```text
GPT-5.6
Gemini XXX
Seedream XXX
```

禁止看到：

```text
API Key
Base URL
Endpoint
真实Provider配置
Provider Secret
model_code
```

---

# 16. 模型选择页面

示例：

```text
项目 AI 配置

────────────────────────

文本模型
[ GPT-5.6             ▼ ]

图片模型
[ Seedream            ▼ ]

视频模型
[ Seedance            ▼ ]

音频模型
[ MiniMax Voice       ▼ ]

          [保存配置]
```

---

# 17. 模型选择接口

```http
GET /api/projects/{projectId}/ai/models
```

返回：

```json
{
  "textModels": [
    {
      "id": 10001,
      "name": "GPT-5.6",
      "description": "适合剧本创作"
    }
  ],
  "imageModels": [],
  "videoModels": [],
  "audioModels": []
}
```

---

# 18. 保存项目模型

```http
PUT /api/projects/{projectId}/ai/config
```

请求：

```json
{
  "textModelId": 10001,
  "imageModelId": 20001,
  "videoModelId": 30001,
  "audioModelId": 40001
}
```

服务端必须校验：

```text
project是否存在
tenant是否匹配
model是否存在
model是否启用
provider是否启用
model是否属于平台
用户是否有项目权限
```

---

# 19. AI Gateway

## 19.1 核心目标

AI Gateway 是整个 P0 的核心。

所有业务调用：

```text
剧本
角色
场景
分镜
图片
视频
配音
```

统一：

```text
AiGateway
```

---

# 20. Gateway 接口

建议：

```java
public interface AiGateway {

    AiTextResponse text(
        AiContext context,
        AiTextRequest request
    );

    AiImageResponse image(
        AiContext context,
        AiImageRequest request
    );

    AiVideoResponse video(
        AiContext context,
        AiVideoRequest request
    );

    AiAudioResponse audio(
        AiContext context,
        AiAudioRequest request
    );
}
```

P0 首先真正实现：

```text
text()
image()
```

视频、音频只保留接口。

---

# 21. AiContext

```java
public class AiContext {

    private Long tenantId;

    private Long userId;

    private Long projectId;

    private Long taskId;

    private Long modelId;

    private String businessType;

    private String traceId;
}
```

作用：

- 数据隔离
- 模型路由
- AI日志
- 任务关联
- 成本统计
- 权限校验

---

# 22. AI请求模型

## 22.1 Text

```java
public class AiTextRequest {

    private String systemPrompt;

    private String userPrompt;

    private Double temperature;

    private Integer maxTokens;

    private Object responseSchema;
}
```

---

## 22.2 Image

```java
public class AiImageRequest {

    private String prompt;

    private String negativePrompt;

    private String size;

    private String aspectRatio;

    private Integer count;

    private List<String> referenceImages;
}
```

---

# 23. Gateway处理流程

```text
业务模块
 ↓
AiGateway
 ↓
校验Context
 ↓
获取Model
 ↓
校验Model
 ↓
获取Provider
 ↓
校验Provider
 ↓
获取平台Secret
 ↓
Model Router
 ↓
Provider Adapter
 ↓
第三方AI
 ↓
标准化Response
 ↓
AI Call Log
 ↓
返回业务
```

---

# 24. Model Router

新增：

```text
AiModelRouter
```

职责：

```text
modelId
 ↓
AiModel
 ↓
providerId
 ↓
AiProvider
 ↓
ProviderAdapter
```

业务层禁止自己查询：

```text
provider
apiKey
baseUrl
```

---

# 25. Provider Adapter

定义统一接口：

```java
public interface AiProviderAdapter {

    String providerCode();

    AiTextResponse text(
        AiProvider provider,
        AiModel model,
        AiTextRequest request
    );

    AiImageResponse image(
        AiProvider provider,
        AiModel model,
        AiImageRequest request
    );
}
```

---

# 26. Adapter 实现

P0建议至少建立：

```text
OpenAiAdapter
GeminiAdapter
VolcengineAdapter
MiniMaxAdapter
```

但实际接入可以分阶段。

建议：

```text
第一阶段
OpenAI + 火山

第二阶段
Gemini + MiniMax
```

Adapter 必须实现：

- URL组装
- Header认证
- Request转换
- Response转换
- 错误码转换
- Provider异常处理

---

# 27. 第三方响应标准化

不同 Provider 返回结构不同。

例如：

```text
OpenAI Response
Gemini Response
火山 Response
```

最终统一为：

```java
public class AiTextResponse {

    private String content;

    private String providerRequestId;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long durationMs;

    private Map<String, Object> metadata;
}
```

业务层只使用统一结果。

---

# 28. AI Gateway异常处理

统一异常：

```text
AI_MODEL_NOT_FOUND
AI_MODEL_DISABLED
AI_PROVIDER_DISABLED
AI_PROVIDER_NOT_SUPPORTED
AI_AUTH_FAILED
AI_RATE_LIMIT
AI_QUOTA_EXCEEDED
AI_PROVIDER_TIMEOUT
AI_PROVIDER_ERROR
AI_RESPONSE_INVALID
```

Provider Adapter 必须负责：

```text
第三方错误
 ↓
平台统一错误
```

---

# 29. API Key 安全

继续复用：

```text
AiSecretCodec
```

要求：

```text
数据库
 ↓
AES/GCM密文
```

调用：

```text
密文
 ↓
后端解密
 ↓
内存使用
 ↓
Provider请求
```

禁止：

```text
日志输出API Key
前端返回完整API Key
异常信息输出API Key
数据库明文保存
```

---

# 30. API Key 前端展示

例如：

```text
API Key

sk-************9X2
```

编辑时：

```text
请输入新的API Key
```

不允许前端拿到原始Key。

---

# 31. AI服务测试改造

当前已有：

```text
POST /ai-service-configs/{id}/test
```

本次改造为：

```http
POST /api/platform/ai/providers/{providerId}/test
```

测试流程：

```text
Provider
 ↓
Provider Adapter
 ↓
发送最小测试请求
 ↓
统一响应
 ↓
记录测试结果
```

测试结果：

```text
SUCCESS
FAILED
TIMEOUT
AUTH_FAILED
```

---

# 32. AI调用日志改造

复用现有：

```text
AiCallLog
```

新增/补充：

| 字段 | 说明 |
|---|---|
| task_id | AI任务 |
| model_id | 模型 |
| provider_id | Provider |
| trace_id | 链路 |
| provider_request_id | 第三方请求ID |
| prompt_tokens | 输入Token |
| completion_tokens | 输出Token |
| total_tokens | 总Token |
| duration_ms | 耗时 |
| estimated_cost | 估算成本 |

日志中：

```text
绝对禁止保存API Key。
```

---

# 33. 权限改造

## 33.1 平台管理员权限

新增或调整：

```text
PLATFORM_AI_PROVIDER_VIEW
PLATFORM_AI_PROVIDER_CREATE
PLATFORM_AI_PROVIDER_EDIT
PLATFORM_AI_PROVIDER_TEST
PLATFORM_AI_PROVIDER_ENABLE
PLATFORM_AI_PROVIDER_DELETE

PLATFORM_AI_MODEL_VIEW
PLATFORM_AI_MODEL_CREATE
PLATFORM_AI_MODEL_EDIT
PLATFORM_AI_MODEL_ENABLE
PLATFORM_AI_MODEL_DELETE
```

---

## 33.2 普通用户权限

用户只需要：

```text
PROJECT_AI_CONFIG_VIEW
PROJECT_AI_CONFIG_EDIT
```

不允许：

```text
Provider管理
API Key管理
Model Code管理
Provider测试
```

---

# 34. 数据权限

Provider：

```text
平台级
```

Model：

```text
平台级
```

Project AI Config：

```text
租户 + 项目级
```

AI Call Log：

```text
平台管理员：平台范围
租户用户：自己的租户
```

---

# 35. 数据库迁移方案

当前：

```text
ai_provider
ai_service_config
```

不建议立即删除 `ai_service_config`。

采用：

```text
旧表
 ↓
数据迁移
 ↓
新Model体系
 ↓
兼容期
 ↓
废弃旧表
```

---

# 36. 数据迁移

当前：

```text
ai_service_config
```

例如：

```text
Provider = OpenAI
Model = GPT-5.6
API Key = xxx
Base URL = xxx
```

迁移：

```text
ai_provider
   ↓
OpenAI

ai_model
   ↓
GPT-5.6

ai_provider_config
   ↓
API Key
Base URL
```

---

# 37. 旧配置映射

建议：

```text
ai_service_config.provider
        ↓
ai_provider.code

ai_service_config.model
        ↓
ai_model.model_code

ai_service_config.api_key_cipher
        ↓
ai_provider_config.api_key_cipher

ai_service_config.base_url
        ↓
ai_provider_config.base_url
```

---

# 38. 兼容策略

第一阶段：

```text
新Gateway
 ↓
优先读取新Model体系
```

如果旧业务仍然传：

```text
serviceConfigId
```

可以临时：

```text
serviceConfigId
 ↓
映射到modelId
 ↓
Gateway
```

这样避免一次性修改所有业务模块。

---

# 39. 业务代码改造原则

旧代码：

```java
AiServiceConfig config =
    aiServiceConfigService.get(...);

callProvider(config);
```

改成：

```java
AiTextResponse response =
    aiGateway.text(
        context,
        request
    );
```

业务层不允许出现：

```java
apiKey
baseUrl
providerCode
modelCode
```

---

# 40. 剧本模块改造示例

当前：

```text
ScriptWorkflowService
 ↓
buildScriptContent()
```

改造后：

```text
ScriptWorkflowService
 ↓
获取 project AI config
 ↓
textModelId
 ↓
AiGateway.text()
 ↓
真实LLM
 ↓
结构化结果
 ↓
Script
```

---

# 41. 图片模块改造示例

当前：

```text
AiImageTaskExecutionService
 ↓
createPlaceholder()
```

改造后：

```text
AiImageTaskExecutionService
 ↓
获取 modelId
 ↓
AiGateway.image()
 ↓
真实图片Provider
 ↓
获取图片URL
 ↓
AiImageStorageService
 ↓
AiImageResult
```

---

# 42. 前端平台管理页面

建议目录：

```text
pages/platform-ai/
├── providers/
│   ├── index.tsx
│   ├── service.ts
│   └── data.ts
│
└── models/
    ├── index.tsx
    ├── service.ts
    └── data.ts
```

---

# 43. Provider页面

列表：

```text
服务商
Provider Code
默认Base URL
模型数量
状态
最后测试时间
更新时间
操作
```

操作：

```text
编辑
测试
启用
禁用
查看模型
```

---

# 44. Model页面

列表：

```text
模型名称
模型编码
所属Provider
服务类型
能力
默认
状态
更新时间
操作
```

操作：

```text
编辑
启用
禁用
设为默认
```

---

# 45. 项目AI配置页面

用户进入项目：

```text
AI模型配置

文本
[ GPT-5.6 ]

图片
[ Seedream ]

视频
[ Seedance ]

语音
[ MiniMax Voice ]
```

不展示任何平台密钥配置。

---

# 46. P0接口清单

## Provider

```http
GET    /api/platform/ai/providers
POST   /api/platform/ai/providers
PUT    /api/platform/ai/providers/{id}
POST   /api/platform/ai/providers/{id}/enable
POST   /api/platform/ai/providers/{id}/disable
POST   /api/platform/ai/providers/{id}/test
```

## Model

```http
GET    /api/platform/ai/models
POST   /api/platform/ai/models
PUT    /api/platform/ai/models/{id}
POST   /api/platform/ai/models/{id}/enable
POST   /api/platform/ai/models/{id}/disable
POST   /api/platform/ai/models/{id}/default
```

## Project

```http
GET /api/projects/{projectId}/ai/models
GET /api/projects/{projectId}/ai/config
PUT /api/projects/{projectId}/ai/config
```

---

# 47. Gateway内部调用

业务层：

```java
AiContext context = AiContext.builder()
    .tenantId(tenantId)
    .userId(userId)
    .projectId(projectId)
    .modelId(modelId)
    .businessType("SCRIPT_GENERATION")
    .traceId(traceId)
    .build();

AiTextResponse response =
    aiGateway.text(context, request);
```

业务层无需知道：

```text
OpenAI
Gemini
火山
MiniMax
```

---

# 48. AI Gateway 不负责什么

Gateway 不负责：

```text
剧本业务逻辑
角色业务逻辑
场景业务逻辑
分镜业务逻辑
积分业务规则
素材业务逻辑
```

Gateway只负责：

```text
模型路由
Provider调用
请求标准化
响应标准化
异常标准化
调用日志
```

---

# 49. P0暂不实现

为了控制范围，以下功能暂不作为本次P0核心：

```text
模型自动Fallback
多Provider智能负载均衡
复杂模型路由策略
按租户配置模型价格
完整套餐计费
AI成本BI分析
模型自动发现
模型自动同步Provider
Prompt管理中心
模型评测中心
模型AB测试
```

这些作为后续 P1/P2。

---

# 50. P0技术验收标准

## Provider

- [ ] Provider为平台级数据
- [ ] 不存在租户级Provider配置
- [ ] API Key加密保存
- [ ] 前端不返回完整API Key
- [ ] 支持启用/禁用
- [ ] 支持测试

## Model

- [ ] Model独立于Provider配置
- [ ] Model关联Provider
- [ ] Model支持服务类型
- [ ] Model支持启用/禁用
- [ ] Model支持默认配置
- [ ] 用户只能看到开放模型

## Project

- [ ] 项目可以选择Model
- [ ] 用户不能配置API Key
- [ ] 用户不能修改Base URL
- [ ] 用户不能填写第三方Model Code

## Gateway

- [ ] 所有AI业务统一经过Gateway
- [ ] 业务模块不能直接调用Provider
- [ ] 支持Model Router
- [ ] 支持Provider Adapter
- [ ] 支持统一错误码
- [ ] 支持统一响应结构

## 安全

- [ ] API Key不进入日志
- [ ] API Key不返回前端
- [ ] Model权限校验完整
- [ ] Tenant隔离完整
- [ ] Project权限校验完整

---

# 51. 开发实施顺序

## 第1阶段：数据库

```text
ai_provider
 ↓
ai_provider_config
 ↓
ai_model
 ↓
ai_model_capability
 ↓
project_ai_config
```

完成：

- 表结构
- 索引
- 唯一约束
- 数据迁移

---

## 第2阶段：平台管理

完成：

```text
Provider管理
Model管理
Capability
默认Model
API Key
测试
```

---

## 第3阶段：Gateway

完成：

```text
AiGateway
AiModelRouter
AiProviderAdapter
```

---

## 第4阶段：Provider接入

优先：

```text
OpenAI
火山
```

然后：

```text
Gemini
MiniMax
```

---

## 第5阶段：现有业务迁移

优先改：

```text
ScriptWorkflowService
AiImageTaskExecutionService
```

把：

```text
Mock
Placeholder
```

逐步替换为：

```text
AiGateway
```

---

# 52. 最终目标

改造完成后：

```text
                     平台管理员
                          │
                          ▼
                   AI Provider
                          │
                  API Key / Base URL
                          │
                          ▼
                      AI Model
                          │
                    Model Capability
                          │
             ┌────────────┴────────────┐
             ▼                         ▼
         项目A                       项目B
             │                         │
         选择模型                   选择模型
             │                         │
             └────────────┬────────────┘
                          ▼
                     AI Gateway
                          │
                    Model Router
                          │
                    Provider Adapter
                          │
            ┌─────────────┼─────────────┐
            ▼             ▼             ▼
          OpenAI        Gemini         火山
            │             │             │
            └─────────────┼─────────────┘
                          ▼
                       AI结果
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
            剧本         图片          视频
```

最终实现：

> **平台负责管理“AI能力”，用户负责选择“AI模型”，业务负责使用“AI能力”。**

这是本项目后续剧本、角色、场景、分镜、图片、视频、配音等所有 AI 功能的统一基础设施。

---

# 53. 本次改造的核心原则

### 原则一：不重复开发

现有：

```text
AiProvider
AiServiceConfig
AiSecretCodec
AiCallLog
AI服务测试
RBAC
```

全部优先复用。

### 原则二：不让业务感知Provider

业务代码只允许：

```text
modelId
AiGateway
```

### 原则三：不让用户接触密钥

API Key永远是：

```text
平台配置
```

### 原则四：Model是用户选择的最小单位

用户看到：

```text
GPT-5.6
Seedream
```

而不是：

```text
OpenAI API
API Key
Base URL
```

### 原则五：先建立统一底座，再扩展AI业务

后续所有：

```text
剧本
角色
场景
分镜
图片
视频
配音
```

统一接入：

```text
AiGateway
```

而不是每个模块自己重新接一套AI服务。
