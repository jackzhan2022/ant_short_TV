package com.antshorttv.operations.alerts;

import org.springframework.jdbc.core.JdbcTemplate;

final class OperationsAlertTestSchema {
    static void permissions(JdbcTemplate jdbc) {
        jdbc.execute("create table platform_permission(id bigint auto_increment primary key,code varchar(100),name varchar(100),resource varchar(100),action varchar(100),created_at timestamp,updated_at timestamp)");
        jdbc.execute("create table platform_role(id bigint primary key,code varchar(100))");
        jdbc.execute("create table platform_role_permission(role_id bigint,permission_id bigint,created_at timestamp)");
    }
}
