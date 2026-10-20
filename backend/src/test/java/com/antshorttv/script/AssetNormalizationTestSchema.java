package com.antshorttv.script;

import org.springframework.jdbc.core.JdbcTemplate;

final class AssetNormalizationTestSchema {
    static void gates(JdbcTemplate jdbc) {
        for(String table:new String[]{"script_asset_extraction_owner","script_asset_normalization_snapshot","ai_execution_task"})
            jdbc.execute("drop table if exists "+table);
        jdbc.execute("create table script_asset_extraction_owner (tenant_id bigint,script_id bigint,execution_id bigint)");
        jdbc.execute("create table ai_execution_task (id bigint primary key,status varchar(32))");
        jdbc.execute("create table script_asset_normalization_snapshot (id bigint primary key,tenant_id bigint,script_id bigint,status varchar(32))");
    }
}
