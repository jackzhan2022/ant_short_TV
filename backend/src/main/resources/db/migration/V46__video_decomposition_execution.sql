alter table video_decomposition_episode add column execution_id bigint null;
alter table video_decomposition_analysis add column execution_id bigint null;
alter table video_decomposition_attempt add column execution_id bigint null;

create unique index uk_video_decomposition_episode_execution
  on video_decomposition_episode (execution_id);
create index idx_video_decomposition_analysis_execution
  on video_decomposition_analysis (execution_id);
create index idx_video_decomposition_attempt_execution
  on video_decomposition_attempt (execution_id);
