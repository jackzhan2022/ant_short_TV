alter table video_decomposition_batch
  modify column project_id bigint null;

alter table video_decomposition_episode
  modify column project_id bigint null;
