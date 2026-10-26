create table video_frame_capture (
  image_result_id bigint primary key,
  tenant_id bigint not null,
  project_id bigint not null,
  source_video_result_id bigint not null,
  storyboard_id bigint not null,
  frame_time_seconds double not null,
  session_token varchar(64) not null,
  created_by bigint not null,
  created_at datetime not null,
  constraint uk_video_frame_capture_session unique (session_token)
);
create index idx_video_frame_capture_source on video_frame_capture (tenant_id, project_id, source_video_result_id);
