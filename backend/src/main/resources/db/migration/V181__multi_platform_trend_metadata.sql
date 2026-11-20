alter table trend_collection_run add column error_code varchar(60) null;
alter table trend_snapshot add column source_date date null;
alter table trend_snapshot add column language varchar(20) not null default 'zh-CN';
alter table trend_snapshot add column ranking_kind varchar(30) not null default 'RANK';
alter table trend_snapshot add column coverage varchar(40) not null default 'FULL_PUBLIC_PAGES';
alter table trend_snapshot add column reported_total int null;
alter table trend_snapshot_entry add column metric_kind varchar(40) not null default 'PLATFORM_HEAT';
alter table trend_snapshot_entry add column episode_count int null;
create index idx_trend_snapshot_source_time on trend_snapshot(source_id, observed_at);
