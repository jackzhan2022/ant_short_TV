alter table team_miniapp add column auth_status varchar(32) not null default 'LEGACY';
create table wechat_component_state (
 id int primary key,
 ticket_cipher text null,
 ticket_time bigint not null default 0,
 token_cipher text null,
 token_expires_at datetime null
);
insert into wechat_component_state (id) values (1);
create table wechat_authorization_request (
 id varchar(36) primary key,
 tenant_id bigint not null,
 operator_id bigint not null,
 state_hash varchar(64) not null unique,
 preauth_hash varchar(64) not null unique,
 status varchar(32) not null,
 appid varchar(32) null,
 created_at datetime not null,
 expires_at datetime not null,
 index idx_wechat_request_team (tenant_id, id)
);
create table wechat_authorizer (
 appid varchar(32) primary key,
 tenant_id bigint null,
 auth_status varchar(32) not null,
 access_cipher text null,
 refresh_cipher text null,
 token_expires_at datetime null,
 scopes text null,
 event_time bigint not null default 0,
 event_type varchar(32) null
);
update permission set name='管理团队小程序授权' where code='MINIAPP:MANAGE';
