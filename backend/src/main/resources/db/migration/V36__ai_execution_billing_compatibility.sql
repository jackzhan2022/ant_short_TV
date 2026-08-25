create table ai_execution_scene_compatibility (
  id bigint primary key auto_increment,
  scene varchar(100) not null,
  point_charge_mode varchar(32) not null,
  point_amount decimal(24,8) not null,
  legacy_charge_timing varchar(32) not null,
  legacy_transaction_type varchar(32) not null,
  migration_status varchar(32) not null,
  enabled boolean not null default true,
  created_at datetime not null,
  updated_at datetime not null,
  constraint uk_ai_execution_scene_compatibility unique (scene)
);

insert into ai_execution_scene_compatibility
  (scene, point_charge_mode, point_amount, legacy_charge_timing,
   legacy_transaction_type, migration_status, enabled, created_at, updated_at)
values
  ('CHARACTER', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('SCENE', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('STORYBOARD_FIRST_FRAME', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('STORYBOARD_VIDEO_GENERATION', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('AI_VOICE_SYNTHESIS', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_generate', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_rewrite', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_global_understanding', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_episode_split', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_episode_summary', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_character_scene_recognition', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('character_extract', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('scene_extract', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('prop_extract', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('storyboard_breakdown', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('prompt_generate', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('video_understanding', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('video_script_draft', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now()),
  ('script_review', 'FIXED', 1, 'BEFORE_PROVIDER_CALL', 'AI_CONSUME', 'LEGACY_ACTIVE', true, now(), now());
