create table media_processing_output_cleanup (
  job_id bigint not null,
  attempt_no int not null,
  cleaned_at datetime not null,
  primary key (job_id, attempt_no),
  constraint fk_media_processing_output_cleanup_job
    foreign key (job_id) references media_processing_job(id) on delete cascade
);
