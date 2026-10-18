create table tenant_workflow_skill (
    tenant_id bigint not null,
    skill_code varchar(128) not null,
    name varchar(200) not null,
    description varchar(2000) not null,
    content longtext not null,
    revision varchar(64) not null,
    created_by bigint not null,
    updated_by bigint not null,
    created_at datetime not null,
    updated_at datetime not null,
    primary key (tenant_id, skill_code)
);
