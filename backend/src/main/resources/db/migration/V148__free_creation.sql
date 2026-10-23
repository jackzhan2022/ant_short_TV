create table free_creation (
    id bigint not null auto_increment primary key,
    tenant_id bigint not null,
    creator_id bigint not null,
    execution_id bigint,
    request_id varchar(100) not null,
    request_hash varchar(64) not null,
    media_type varchar(16) not null,
    model_id bigint not null,
    model_name varchar(255) not null,
    provider_code varchar(64) not null,
    prompt longtext not null,
    parameters_json longtext not null,
    external_task_id varchar(255),
    provider_result_json longtext,
    billing_attempt_id bigint,
    billing_call_log_id bigint,
    provider_started_at datetime,
    created_at datetime not null,
    updated_at datetime not null,
    constraint uk_free_creation_request unique(tenant_id,creator_id,request_id),
    constraint uk_free_creation_execution unique(execution_id)
);
create index ix_free_creation_team on free_creation(tenant_id,created_at,id);
create index ix_free_creation_mine on free_creation(tenant_id,creator_id,created_at,id);
create table free_creation_media (
    id bigint not null auto_increment primary key,
    tenant_id bigint not null,
    creation_id bigint not null,
    role varchar(16) not null,
    position int not null,
    kind varchar(16) not null,
    file_name varchar(255) not null,
    object_key varchar(1024) not null,
    thumbnail_key varchar(1024),
    mime_type varchar(100) not null,
    format varchar(32) not null,
    file_size bigint not null,
    width int,
    height int,
    duration_seconds decimal(16,4),
    fps decimal(16,4),
    created_at datetime not null,
    constraint uk_free_creation_media_position unique(creation_id,role,position)
);
create index ix_free_creation_media_tenant on free_creation_media(tenant_id,creation_id);
