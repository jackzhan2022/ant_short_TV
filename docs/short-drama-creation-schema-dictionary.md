# 短剧创作数据表字段字典

> 范围：短剧创作主生产链（项目、剧本、剧集、解析、资产、分镜、生成、成片、统一 AI 执行）。不含账号/租户基础表、商业订阅、视频拆剧与剧本审核模块。
>
> 依据：当前代码库的 Flyway SQL/Java 迁移。索引、外键与枚举值未在本文逐条展开。

## 项目与配置

### `project`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `name` | varchar(200) not null |
| `code` | varchar(50) not null |
| `description` | text null |
| `cover_url` | varchar(500) null |
| `owner_id` | bigint not null |
| `status` | varchar(30) not null |
| `start_date` | date null |
| `end_date` | date null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `aspect_ratio` | varchar(16) null |
| `file_format` | varchar(32) null |
| `script_type` | varchar(32) null |
| `breakdown_strength` | varchar(32) null |
| `cover_source` | varchar(32) null |
| `visual_style` | varchar(120) null |
| `initial_script_content` | longtext null |

### `project_member`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `user_id` | bigint not null |
| `role_id` | bigint not null |
| `joined_at` | datetime not null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `project_role`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `name` | varchar(100) not null |
| `code` | varchar(50) not null |
| `description` | varchar(500) null |
| `is_system` | boolean not null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `project_role_permission`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `role_id` | bigint not null |
| `permission_id` | bigint not null |
| `created_at` | datetime not null |

### `project_operation_log`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint null |
| `user_id` | bigint null |
| `operation_type` | varchar(64) not null |
| `resource_type` | varchar(64) not null |
| `resource_id` | bigint null |
| `before_data` | text null |
| `after_data` | text null |
| `ip` | varchar(64) null |
| `user_agent` | varchar(512) null |
| `created_at` | datetime not null |

### `project_ai_config`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `text_model_id` | bigint null |
| `image_model_id` | bigint null |
| `video_model_id` | bigint null |
| `audio_model_id` | bigint null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

## 剧本与剧集

### `script`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `title` | varchar(200) not null |
| `source_type` | varchar(32) not null |
| `content` | longtext not null |
| `status` | varchar(32) not null |
| `current_version_id` | bigint null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |

### `script_version`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `script_id` | bigint not null |
| `version_no` | int not null |
| `source_type` | varchar(32) not null |
| `input_summary` | longtext null |
| `content` | longtext not null |
| `ai_call_log_id` | bigint null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `execution_id` | bigint null |

### `script_episode`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `script_id` | bigint not null |
| `script_version_id` | bigint not null |
| `stable_key` | varchar(160) not null |
| `episode_no` | int not null |
| `title` | varchar(200) null |
| `summary` | text null |
| `content` | longtext not null |
| `content_fingerprint` | varchar(64) not null |
| `heading_key` | varchar(200) null |
| `reconciliation_status` | varchar(32) not null |
| `status` | varchar(32) not null |
| `retired_at` | datetime null |
| `active_stable_marker` | varchar(240) generated: active stable key when retired_at is null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `script_episode_version`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `episode_id` | bigint not null |
| `version_no` | int not null |
| `content` | longtext not null |
| `status` | varchar(32) not null |
| `is_current` | boolean not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `current_marker` | varchar(240) generated: current-version uniqueness marker |

## AI 解析与操作

### `script_analysis_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `script_id` | bigint not null |
| `script_version_id` | bigint not null |
| `workflow_code` | varchar(64) not null |
| `status` | varchar(32) not null |
| `current_stage` | varchar(64) null |
| `overall_progress` | int not null default 0 |
| `current_action` | varchar(500) null |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `idempotency_key` | varchar(200) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `completed_at` | datetime null |
| `execution_id` | bigint null |

### `script_analysis_stage`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `task_id` | bigint not null |
| `stage_code` | varchar(64) not null |
| `stage_order` | int not null |
| `status` | varchar(32) not null |
| `progress_percent` | int not null default 0 |
| `completed_units` | int not null default 0 |
| `total_units` | int not null default 0 |
| `current_action` | varchar(500) null |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `attempt_no` | int not null default 0 |
| `retryable` | boolean not null default false |
| `started_at` | datetime null |
| `finished_at` | datetime null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `script_analysis_result`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `task_id` | bigint not null |
| `stage_id` | bigint not null |
| `result_type` | varchar(64) not null |
| `schema_version` | varchar(32) not null |
| `status` | varchar(32) not null |
| `raw_response` | longtext null |
| `normalized_json` | longtext null |
| `provider_request_id` | varchar(128) null |
| `ai_call_log_id` | bigint null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `duration_ms` | bigint null |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `retryable` | boolean not null default false |
| `execution_id` | bigint null |

### `script_analysis_config_snapshot`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `task_id` | bigint not null |
| `agent_code` | varchar(128) not null |
| `agent_version_no` | int null |
| `skill_versions_json` | longtext null |
| `model_parameter_profile_id` | bigint null |
| `model_parameter_version_no` | int null |
| `snapshot_json` | longtext not null |
| `created_at` | datetime not null |

### `script_ai_operation`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `operation_type` | varchar(64) not null |
| `script_id` | bigint null |
| `script_version_id` | bigint null |
| `redacted_input_json` | longtext not null |
| `idempotency_key` | varchar(200) not null |
| `status` | varchar(32) not null |
| `execution_id` | bigint null |
| `result_type` | varchar(64) null |
| `result_id` | bigint null |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `completed_at` | datetime null |

## 资产与资产审核

### `character_asset`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `name` | varchar(100) not null |
| `role_type` | varchar(32) not null |
| `gender` | varchar(32) null |
| `age_range` | varchar(32) null |
| `identity` | varchar(200) null |
| `personality` | varchar(500) null |
| `appearance` | varchar(500) null |
| `relationship_text` | varchar(500) null |
| `plot_function` | varchar(500) null |
| `prompt` | text null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `main_image_url` | varchar(1000) null |
| `main_image_result_id` | bigint null |
| `merge_target_id` | bigint null |

### `scene_asset`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `name` | varchar(100) not null |
| `scene_type` | varchar(32) not null |
| `time_atmosphere` | varchar(100) null |
| `description` | text null |
| `visual_style` | varchar(300) null |
| `plot_reference` | varchar(500) null |
| `prompt` | text null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `main_image_url` | varchar(1000) null |
| `main_image_result_id` | bigint null |
| `merge_target_id` | bigint null |

### `prop_asset`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `name` | varchar(100) not null |
| `prop_type` | varchar(32) not null |
| `appearance` | varchar(500) null |
| `plot_function` | varchar(500) null |
| `related_character` | varchar(200) null |
| `prompt` | text null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `merge_target_id` | bigint null |
| `main_image_url` | varchar(1000) null |
| `main_image_result_id` | bigint null |

### `script_asset_normalization_run`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `script_id` | bigint not null |
| `script_version_id` | bigint not null |
| `analysis_task_id` | bigint null |
| `analysis_stage_id` | bigint null |
| `analysis_result_id` | bigint null |
| `execution_id` | bigint null |
| `attempt_id` | bigint null |
| `ai_call_log_id` | bigint null |
| `idempotency_key` | varchar(200) not null |
| `schema_version` | varchar(32) not null |
| `status` | varchar(32) not null |
| `raw_response` | longtext null |
| `normalized_json` | longtext null |
| `error_code` | varchar(64) null |
| `error_message` | text null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `script_asset_candidate`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `run_id` | bigint not null |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `asset_type` | varchar(32) not null |
| `source_index` | int not null |
| `source_key` | varchar(64) null |
| `name` | varchar(100) null |
| `normalized_name` | varchar(100) null |
| `candidate_json` | longtext not null |
| `validation_status` | varchar(32) not null |
| `validation_errors_json` | longtext null |
| `duplicate_group_key` | varchar(160) null |
| `proposed_target_id` | bigint null |
| `match_type` | varchar(32) null |
| `match_confidence` | decimal(5,4) null |
| `match_evidence_json` | longtext null |
| `review_status` | varchar(32) not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `script_asset_candidate_alias`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `candidate_id` | bigint not null |
| `alias_name` | varchar(100) not null |
| `normalized_alias` | varchar(100) not null |
| `source` | varchar(32) not null |
| `evidence_json` | longtext null |
| `created_at` | datetime not null |

### `script_asset_promotion_decision`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `candidate_id` | bigint not null |
| `decision_type` | varchar(32) not null |
| `requested_target_id` | bigint null |
| `result_asset_id` | bigint null |
| `idempotency_key` | varchar(200) not null |
| `status` | varchar(32) not null |
| `error_code` | varchar(64) null |
| `error_message` | text null |
| `decided_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `asset_visual_variant`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `asset_type` | varchar(32) not null |
| `asset_id` | bigint not null |
| `name` | varchar(100) not null |
| `appearance` | text null |
| `prompt` | text null |
| `source_type` | varchar(32) not null |
| `generation_status` | varchar(32) not null |
| `generation_task_id` | bigint null |
| `current_image_result_id` | bigint null |
| `current_image_url` | varchar(1000) null |
| `generation_error_code` | varchar(64) null |
| `generation_error_message` | text null |
| `is_primary` | boolean not null |
| `primary_marker` | varchar(180) generated: active primary-variant uniqueness marker |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |

### `asset_visual_variant_episode`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `script_id` | bigint not null |
| `episode_id` | bigint not null |
| `asset_type` | varchar(32) not null |
| `asset_id` | bigint not null |
| `variant_id` | bigint not null |
| `is_preferred` | boolean not null |
| `binding_status` | varchar(32) not null |
| `active_binding_marker` | varchar(160) generated: active binding uniqueness marker |
| `preferred_marker` | varchar(220) generated: preferred binding uniqueness marker |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `retired_at` | datetime null |

## 分镜与生成素材

### `storyboard`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `script_id` | bigint null |
| `episode_no` | int not null |
| `shot_no` | int not null |
| `scene_no` | varchar(50) null |
| `shot_type` | varchar(50) null |
| `visual_description` | text not null |
| `characters` | varchar(500) null |
| `actions` | text null |
| `dialogue` | text null |
| `scene` | varchar(200) null |
| `props` | varchar(500) null |
| `mood` | varchar(200) null |
| `duration_seconds` | int null |
| `image_prompt` | text null |
| `video_prompt` | text null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `first_frame_image_url` | varchar(1000) null |
| `first_frame_result_id` | bigint null |
| `first_frame_image_id` | bigint null |
| `first_frame_url` | varchar(1000) null |
| `current_video_result_id` | bigint null |
| `current_video_material_id` | bigint null |
| `current_video_url` | varchar(1000) null |
| `current_voice_result_id` | bigint null |
| `current_audio_url` | varchar(1000) null |
| `current_subtitle_id` | bigint null |
| `current_subtitle_url` | varchar(1000) null |
| `current_shot_result_id` | bigint null |
| `current_shot_video_url` | varchar(1000) null |

### `ai_image_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `task_type` | varchar(32) not null |
| `target_type` | varchar(32) not null |
| `target_id` | bigint not null |
| `provider_code` | varchar(64) not null |
| `model` | varchar(128) not null |
| `prompt` | text not null |
| `negative_prompt` | text null |
| `reference_images` | text null |
| `aspect_ratio` | varchar(32) not null |
| `image_count` | int not null |
| `style` | varchar(64) null |
| `quality` | varchar(32) null |
| `seed` | varchar(64) null |
| `status` | varchar(32) not null |
| `error_message` | text null |
| `started_at` | datetime null |
| `completed_at` | datetime null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `ai_call_log_id` | bigint null |
| `model_id` | bigint null |
| `execution_id` | bigint null |
| `client_idempotency_key` | varchar(200) null |

### `ai_image_result`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `task_id` | bigint not null |
| `target_type` | varchar(32) not null |
| `target_id` | bigint not null |
| `image_url` | varchar(1000) not null |
| `storage_path` | varchar(1000) null |
| `thumbnail_url` | varchar(1000) null |
| `width` | int null |
| `height` | int null |
| `file_size` | bigint null |
| `material_id` | bigint null |
| `is_selected` | boolean not null |
| `status` | varchar(32) not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `execution_id` | bigint null |

### `material`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `material_type` | varchar(32) not null |
| `source_type` | varchar(32) not null |
| `source_task_id` | bigint null |
| `name` | varchar(200) not null |
| `url` | varchar(1000) not null |
| `storage_path` | varchar(1000) null |
| `mime_type` | varchar(100) null |
| `file_size` | bigint null |
| `width` | int null |
| `height` | int null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `source_result_id` | bigint null |
| `cover_url` | varchar(1000) null |
| `duration_seconds` | decimal(8,2) null |
| `format` | varchar(32) null |
| `status` | varchar(32) null |
| `updated_at` | datetime null |
| `deleted_at` | datetime null |

### `ai_video_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `provider_code` | varchar(64) not null |
| `model` | varchar(128) not null |
| `prompt` | text not null |
| `negative_prompt` | text null |
| `first_frame_image_id` | bigint null |
| `first_frame_url` | varchar(1000) not null |
| `last_frame_image_id` | bigint null |
| `last_frame_url` | varchar(1000) null |
| `reference_images` | text null |
| `duration_seconds` | int not null |
| `aspect_ratio` | varchar(32) not null |
| `resolution` | varchar(32) null |
| `motion_strength` | varchar(32) null |
| `camera_movement` | varchar(64) null |
| `random_seed` | bigint null |
| `external_task_id` | varchar(256) null |
| `external_status` | varchar(64) null |
| `status` | varchar(32) not null |
| `error_message` | text null |
| `submitted_at` | datetime null |
| `started_at` | datetime null |
| `completed_at` | datetime null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |
| `request_hash` | varchar(64) null |
| `poll_retry_count` | int not null default 0 |
| `last_poll_at` | datetime null |
| `next_poll_at` | datetime null |
| `execution_token` | varchar(64) null |
| `execution_phase` | varchar(32) null |
| `execution_version` | int not null default 0 |
| `claimed_at` | datetime null |
| `heartbeat_at` | datetime null |
| `execution_timeout_at` | datetime null |
| `retryable` | boolean not null default false |
| `execution_id` | bigint null |
| `model_id` | bigint null |

### `ai_video_task_attempt`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `task_id` | bigint not null |
| `attempt_no` | int not null |
| `phase` | varchar(32) not null |
| `status` | varchar(32) not null |
| `idempotency_key` | varchar(200) null |
| `provider_request_id` | varchar(128) null |
| `ai_call_log_id` | bigint null |
| `retryable` | boolean not null default false |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `started_at` | datetime not null |
| `finished_at` | datetime null |

### `ai_video_result`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `task_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `video_url` | varchar(1000) not null |
| `storage_path` | varchar(1000) not null |
| `cover_url` | varchar(1000) null |
| `duration_seconds` | decimal(8,2) null |
| `width` | int null |
| `height` | int null |
| `file_size` | bigint null |
| `format` | varchar(32) null |
| `material_id` | bigint null |
| `is_selected` | boolean not null |
| `status` | varchar(32) not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `execution_id` | bigint null |

### `ai_voice_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `provider_code` | varchar(64) not null |
| `model` | varchar(128) not null |
| `voice_type` | varchar(32) not null |
| `speaker_name` | varchar(100) null |
| `voice_id` | varchar(128) not null |
| `text_content` | text not null |
| `speed` | decimal(4,2) not null |
| `pitch` | decimal(4,2) not null |
| `volume` | decimal(4,2) not null |
| `status` | varchar(32) not null |
| `error_message` | text null |
| `started_at` | datetime null |
| `completed_at` | datetime null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |

### `ai_voice_result`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `task_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `audio_url` | varchar(1000) not null |
| `storage_path` | varchar(1000) not null |
| `duration_seconds` | decimal(8,2) null |
| `file_size` | bigint null |
| `format` | varchar(32) null |
| `material_id` | bigint null |
| `is_selected` | boolean not null |
| `status` | varchar(32) not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `storyboard_subtitle`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `voice_result_id` | bigint null |
| `subtitle_type` | varchar(32) not null |
| `content` | text not null |
| `srt_url` | varchar(1000) not null |
| `style_config` | text null |
| `is_selected` | boolean not null |
| `status` | varchar(32) not null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

## 镜头与单集成片

### `shot_compose_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `voice_result_id` | bigint null |
| `subtitle_id` | bigint null |
| `compose_config` | text null |
| `status` | varchar(32) not null |
| `error_message` | text null |
| `started_at` | datetime null |
| `completed_at` | datetime null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |

### `shot_compose_result`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `task_id` | bigint not null |
| `storyboard_id` | bigint not null |
| `video_url` | varchar(1000) not null |
| `storage_path` | varchar(1000) not null |
| `cover_url` | varchar(1000) null |
| `duration_seconds` | decimal(8,2) null |
| `width` | int null |
| `height` | int null |
| `file_size` | bigint null |
| `format` | varchar(32) null |
| `material_id` | bigint null |
| `is_selected` | boolean not null |
| `status` | varchar(32) not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `episode_compose_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `episode_no` | int not null |
| `task_name` | varchar(200) not null |
| `compose_config` | text null |
| `storyboard_count` | int not null |
| `total_duration_seconds` | decimal(10,2) null |
| `status` | varchar(32) not null |
| `error_message` | text null |
| `started_at` | datetime null |
| `completed_at` | datetime null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `deleted_at` | datetime null |

### `episode_compose_item`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `task_id` | bigint not null |
| `episode_no` | int not null |
| `storyboard_id` | bigint not null |
| `storyboard_order` | int not null |
| `shot_result_id` | bigint null |
| `video_url` | varchar(1000) null |
| `duration_seconds` | decimal(8,2) null |
| `width` | int null |
| `height` | int null |
| `status` | varchar(32) not null |
| `error_message` | text null |
| `created_at` | datetime not null |

### `episode_video_version`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `episode_no` | int not null |
| `compose_task_id` | bigint not null |
| `version_no` | int not null |
| `version_name` | varchar(200) not null |
| `video_url` | varchar(1000) not null |
| `storage_path` | varchar(1000) not null |
| `cover_url` | varchar(1000) null |
| `duration_seconds` | decimal(10,2) null |
| `width` | int null |
| `height` | int null |
| `file_size` | bigint null |
| `format` | varchar(32) null |
| `material_id` | bigint null |
| `is_current` | boolean not null |
| `status` | varchar(32) not null |
| `current_marker` | varchar(100) generated: current active episode-video uniqueness marker |
| `created_by` | bigint not null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |

### `episode_export_record`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `project_id` | bigint not null |
| `episode_no` | int not null |
| `video_version_id` | bigint not null |
| `export_type` | varchar(32) not null |
| `export_status` | varchar(32) not null |
| `file_name` | varchar(255) null |
| `file_size` | bigint null |
| `download_url` | varchar(1000) null |
| `error_message` | text null |
| `created_by` | bigint not null |
| `created_at` | datetime not null |

## 统一执行

### `ai_execution_task`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `tenant_id` | bigint not null |
| `user_id` | bigint not null |
| `project_id` | bigint null |
| `scene` | varchar(64) not null |
| `capability` | varchar(64) not null |
| `business_type` | varchar(64) not null |
| `business_id` | bigint null |
| `requested_model_id` | bigint null |
| `resolved_model_id` | bigint null |
| `redacted_input_json` | text null |
| `status` | varchar(32) not null |
| `phase` | varchar(64) not null |
| `progress` | int not null default 0 |
| `execution_version` | int not null default 1 |
| `client_idempotency_key` | varchar(200) not null |
| `trace_id` | varchar(128) not null |
| `priority` | int not null default 100 |
| `next_run_at` | datetime null |
| `claim_token` | varchar(64) null |
| `claimed_at` | datetime null |
| `heartbeat_at` | datetime null |
| `claim_expires_at` | datetime null |
| `retryable` | boolean not null default false |
| `result_type` | varchar(64) null |
| `result_id` | bigint null |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `usage_cost_status` | varchar(32) not null default 'PENDING' |
| `provider_cost_summary_json` | text null |
| `point_settlement_status` | varchar(32) not null default 'PENDING' |
| `reserved_points` | decimal(24,8) not null default 0 |
| `settled_points` | decimal(24,8) not null default 0 |
| `released_points` | decimal(24,8) not null default 0 |
| `started_at` | datetime null |
| `completed_at` | datetime null |
| `canceled_at` | datetime null |
| `created_at` | datetime not null |
| `updated_at` | datetime not null |
| `source_execution_id` | bigint null |
| `root_execution_id` | bigint null |
| `cost_price_version_id` | bigint null |
| `point_price_version_id` | bigint null |
| `commercial_subscription_id` | bigint null |
| `commercial_package_version_id` | bigint null |
| `pre_discount_points` | decimal(24,8) null |
| `discount_rate` | decimal(24,8) not null default 1 |
| `final_points` | decimal(24,8) null |

### `ai_execution_attempt`

| 字段 | 定义 |
| --- | --- |
| `id` | bigint primary key auto_increment |
| `execution_id` | bigint not null |
| `execution_version` | int not null |
| `phase` | varchar(64) not null |
| `attempt_no` | int not null |
| `status` | varchar(32) not null |
| `idempotency_key` | varchar(200) not null |
| `provider_contacted` | boolean not null default false |
| `provider_id` | bigint null |
| `model_id` | bigint null |
| `provider_request_id` | varchar(128) null |
| `external_task_id` | varchar(256) null |
| `ai_call_log_id` | bigint null |
| `transport_outcome` | varchar(32) null |
| `business_outcome` | varchar(32) null |
| `retryable` | boolean not null default false |
| `retry_count` | int not null default 0 |
| `next_retry_at` | datetime null |
| `error_code` | varchar(64) null |
| `error_message` | varchar(1000) null |
| `started_at` | datetime not null |
| `provider_contacted_at` | datetime null |
| `finished_at` | datetime null |
