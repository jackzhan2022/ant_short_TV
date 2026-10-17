create table trend_collection_run (
    id varchar(36) primary key,
    source_id varchar(40) not null,
    operator_id bigint null,
    status varchar(20) not null,
    started_at datetime(6) not null,
    finished_at datetime(6) null,
    snapshot_id varchar(36) null,
    entry_count int not null default 0,
    error_message varchar(1000) null
);
create index idx_trend_run_source_time on trend_collection_run(source_id, started_at);

create table trend_collection_page (
    run_id varchar(36) not null,
    page_number int not null,
    url varchar(500) not null,
    http_status int not null,
    content_type varchar(200) not null,
    fetched_at datetime(6) not null,
    body longtext not null,
    primary key(run_id, page_number),
    foreign key(run_id) references trend_collection_run(id)
);

create table trend_snapshot (
    id varchar(36) primary key,
    source_id varchar(40) not null,
    title varchar(100) not null,
    market varchar(20) not null,
    platform varchar(40) not null,
    content_type varchar(30) not null,
    source_type varchar(30) not null,
    source_url varchar(500) not null,
    observed_date date not null,
    observed_at datetime(6) not null,
    source_updated_text varchar(300) not null,
    page_count int not null,
    entry_count int not null,
    fingerprint varchar(64) not null,
    parser_version varchar(40) not null,
    unique(source_id, observed_date, fingerprint)
);
create index idx_trend_snapshot_date on trend_snapshot(observed_date, observed_at);

create table trend_snapshot_entry (
    snapshot_id varchar(36) not null,
    rank_position int not null,
    external_id varchar(40) not null,
    title varchar(500) not null,
    content_type varchar(30) not null,
    detail_url varchar(1000) not null,
    cover_url varchar(2000) not null,
    metric_text varchar(200) not null,
    metric_value decimal(24,6) null,
    metric_unit varchar(50) null,
    tags_json text not null,
    primary key(snapshot_id, rank_position),
    unique(snapshot_id, external_id),
    foreign key(snapshot_id) references trend_snapshot(id)
);

insert into platform_permission(code, name, resource, action, created_at, updated_at)
select 'PLATFORM_TREND_COLLECT', '采集短剧趋势', 'PLATFORM_TREND', 'COLLECT', now(), now()
where not exists(select 1 from platform_permission where code='PLATFORM_TREND_COLLECT');
insert into platform_role_permission(role_id, permission_id, created_at)
select r.id, p.id, now() from platform_role r join platform_permission p on p.code='PLATFORM_TREND_COLLECT'
where r.code='PLATFORM_ADMIN' and not exists(
    select 1 from platform_role_permission rp where rp.role_id=r.id and rp.permission_id=p.id
);
