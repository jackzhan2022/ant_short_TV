alter table auth_session add column impersonated_user_id bigint null;
alter table auth_session add column impersonated_token_version bigint null;
alter table auth_session add column impersonation_tenant_id bigint null;
create index idx_auth_session_impersonated_user on auth_session (impersonated_user_id, status);
