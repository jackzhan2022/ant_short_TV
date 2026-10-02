package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StoryboardAssetReferenceMigrationTest {

    @Test
    void createsOrderedStoryboardAssetReferenceSchema() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:storyboard_asset_reference;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("""
            select version from flyway_schema_history
             where version is not null order by installed_rank desc limit 1
            """, String.class)).isEqualTo("129");
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.tables
             where lower(table_name) = 'storyboard_asset_reference'
            """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
            select count(distinct lower(column_name)) from information_schema.columns
             where lower(table_name) = 'storyboard_asset_reference'
               and lower(column_name) in (
                 'id', 'tenant_id', 'project_id', 'storyboard_id', 'asset_type', 'asset_id',
                 'variant_id', 'reference_role', 'sort_order', 'resolution_status', 'source_type',
                 'source_name', 'locked_by_user', 'generated_by_run_id', 'created_by', 'created_at',
                 'updated_at', 'retired_at', 'active_order_marker'
               )
            """, Integer.class)).isEqualTo(19);
        assertThat(jdbc.queryForObject("""
            select count(distinct lower(index_name)) from information_schema.indexes
             where lower(table_name) = 'storyboard_asset_reference'
               and lower(index_name) in (
                 'idx_storyboard_asset_reference_project',
                 'idx_storyboard_asset_reference_storyboard'
               )
            """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.table_constraints
             where lower(table_name) = 'storyboard_asset_reference'
               and lower(constraint_name) in (
                 'uk_storyboard_asset_reference_order',
                 'fk_storyboard_asset_reference_storyboard',
                 'fk_storyboard_asset_reference_variant'
               )
            """, Integer.class)).isEqualTo(3);
    }
}
