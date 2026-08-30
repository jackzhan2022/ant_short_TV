create table video_decomposition_script_result (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  batch_id bigint not null,
  episode_id bigint not null,
  analysis_id bigint not null,
  ai_call_log_id bigint null,
  content longtext not null,
  format_version varchar(32) not null,
  created_at datetime not null,
  unique key uk_video_decomposition_script_result_episode (episode_id),
  index idx_video_decomposition_script_result_batch (tenant_id, batch_id, episode_id),
  constraint fk_video_decomposition_script_result_batch
    foreign key (batch_id) references video_decomposition_batch(id),
  constraint fk_video_decomposition_script_result_episode
    foreign key (episode_id) references video_decomposition_episode(id),
  constraint fk_video_decomposition_script_result_analysis
    foreign key (analysis_id) references video_decomposition_analysis(id),
  constraint fk_video_decomposition_script_result_call_log
    foreign key (ai_call_log_id) references ai_call_log(id)
);
