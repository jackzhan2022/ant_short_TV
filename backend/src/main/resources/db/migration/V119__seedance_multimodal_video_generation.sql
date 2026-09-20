update ai_provider
   set recommended_models = 'Seedance 2.0 mini,Seedance 2.0 Fast,Seedance 2.0 Standard,Seedance 2.5',
       description = '火山方舟 Seedance 多模态视频生成模型',
       updated_at = now()
 where code = 'VOLCENGINE_ARK';

insert ignore into ai_model
  (provider_id, code, name, model_code, service_type, description, status, is_default,
   sort, config_json, created_at, updated_at)
select id, 'SEEDANCE_2_0_MINI', 'Seedance 2.0 mini', '__SEEDANCE_2_0_MINI_ENDPOINT_ID__',
       'VIDEO', '火山方舟 Seedance 2.0 mini，Endpoint ID 待平台配置', 'DISABLED', true,
       330, null, now(), now()
  from ai_provider
 where code = 'VOLCENGINE_ARK';

insert ignore into ai_model_capability
  (model_id, capability, status, config_json, created_at, updated_at)
select model.id, 'VIDEO_GENERATION', 'DISABLED', null, now(), now()
  from ai_model model
 where model.code = 'SEEDANCE_2_0_MINI';

update ai_model
   set name = 'Seedance 2.0 mini',
       description = '火山方舟 Seedance 2.0 mini，Endpoint ID 由平台配置',
       is_default = true,
       sort = 330,
       config_json = '{"videoGeneration":{"duration":{"min":4,"max":15,"intelligent":true},"resolutions":["480p","720p"],"image":{"formats":["jpeg","jpg","png","webp","bmp","tiff","gif","heic","heif"],"minCount":1,"maxCount":9,"maxBytes":29999999,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000},"video":{"formats":["mp4","mov"],"maxCount":3,"maxTotalDurationSeconds":15,"minDurationSeconds":2,"maxDurationSeconds":15,"maxBytes":209715200,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000,"minPixels":407696,"maxPixels":8295044,"minFps":24,"maxFps":60},"audio":{"formats":["wav","mp3"],"maxCount":3,"maxTotalDurationSeconds":15,"minDurationSeconds":2,"maxDurationSeconds":15,"maxBytes":15728640}}}',
       updated_at = now()
 where code = 'SEEDANCE_2_0_MINI';

update ai_model
   set name = 'Seedance 2.0 Fast',
       description = '火山方舟 Seedance 2.0 Fast，Endpoint ID 由平台配置',
       is_default = false,
       sort = 320,
       config_json = '{"videoGeneration":{"duration":{"min":4,"max":15,"intelligent":true},"resolutions":["480p","720p"],"image":{"formats":["jpeg","jpg","png","webp","bmp","tiff","gif","heic","heif"],"minCount":1,"maxCount":9,"maxBytes":29999999,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000},"video":{"formats":["mp4","mov"],"maxCount":3,"maxTotalDurationSeconds":15,"minDurationSeconds":2,"maxDurationSeconds":15,"maxBytes":209715200,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000,"minPixels":407696,"maxPixels":8295044,"minFps":24,"maxFps":60},"audio":{"formats":["wav","mp3"],"maxCount":3,"maxTotalDurationSeconds":15,"minDurationSeconds":2,"maxDurationSeconds":15,"maxBytes":15728640}}}',
       updated_at = now()
 where code = 'SEEDANCE_2_0_FAST';

update ai_model
   set name = 'Seedance 2.0 Standard',
       description = '火山方舟 Seedance 2.0 Standard，Endpoint ID 由平台配置',
       is_default = false,
       sort = 310,
       config_json = '{"videoGeneration":{"duration":{"min":4,"max":15,"intelligent":true},"resolutions":["480p","720p","1080p","4k"],"image":{"formats":["jpeg","jpg","png","webp","bmp","tiff","gif","heic","heif"],"minCount":1,"maxCount":9,"maxBytes":29999999,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000},"video":{"formats":["mp4","mov"],"maxCount":3,"maxTotalDurationSeconds":15,"minDurationSeconds":2,"maxDurationSeconds":15,"maxBytes":209715200,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000,"minPixels":407696,"maxPixels":8295044,"minFps":24,"maxFps":60},"audio":{"formats":["wav","mp3"],"maxCount":3,"maxTotalDurationSeconds":15,"minDurationSeconds":2,"maxDurationSeconds":15,"maxBytes":15728640}}}',
       updated_at = now()
 where code = 'SEEDANCE_2_0_STANDARD';

update ai_model
   set name = 'Seedance 2.5',
       description = '火山方舟 Seedance 2.5，Endpoint ID 由平台配置',
       is_default = false,
       sort = 300,
       config_json = '{"videoGeneration":{"duration":{"min":4,"max":30,"intelligent":true},"resolutions":["480p","720p","1080p"],"image":{"formats":["jpeg","jpg","png","webp","bmp","tiff","gif","heic","heif"],"minCount":1,"maxCount":30,"maxBytes":29999999,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000},"video":{"formats":["mp4","mov"],"maxCount":10,"maxTotalDurationSeconds":30,"minDurationSeconds":2,"maxDurationSeconds":30,"maxBytes":209715200,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000,"minPixels":407696,"maxPixels":8295044,"minFps":24,"maxFps":60},"audio":{"formats":["wav","mp3"],"maxCount":10,"maxTotalDurationSeconds":30,"minDurationSeconds":2,"maxDurationSeconds":30,"maxBytes":15728640}}}',
       updated_at = now()
 where code = 'SEEDANCE_2_5';

alter table material add column fps decimal(8,3) null;

alter table project add column video_resolution varchar(32) not null default '720p';
alter table project add column video_generate_audio boolean not null default true;
alter table project add column video_watermark boolean not null default false;

alter table ai_video_task add column compiled_prompt longtext null;
alter table ai_video_task add column generate_audio boolean not null default true;
alter table ai_video_task add column watermark boolean not null default false;
alter table ai_video_task add column request_snapshot_json longtext null;
alter table ai_video_task add column provider_result_metadata_json longtext null;

create table ai_video_task_reference (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  storyboard_id bigint not null,
  media_type varchar(16) not null,
  media_index int not null,
  provider_role varchar(32) not null,
  source_type varchar(32) not null,
  source_id bigint not null,
  variant_id bigint null,
  display_name varchar(300) not null,
  compiled_label varchar(32) not null,
  object_storage_path varchar(1000) null,
  provider_url varchar(2000) not null,
  format varchar(32) not null,
  file_size bigint null,
  width int null,
  height int null,
  duration_seconds decimal(10,3) null,
  fps decimal(8,3) null,
  sort_order int not null,
  created_at datetime not null,
  unique key uk_ai_video_task_reference_index (task_id, media_type, media_index),
  index idx_ai_video_task_reference_task (tenant_id, project_id, task_id, sort_order),
  index idx_ai_video_task_reference_source (tenant_id, project_id, source_type, source_id),
  constraint fk_ai_video_task_reference_task foreign key (task_id) references ai_video_task(id)
);

update storyboard
   set prompt_document_json = null,
       material_binding_status = 'LEGACY',
       updated_at = now()
 where prompt_document_json like '%"version":1%'
    or prompt_document_json like '%"version": 1%'
    or prompt_document_json like '%"version" : 1%';

