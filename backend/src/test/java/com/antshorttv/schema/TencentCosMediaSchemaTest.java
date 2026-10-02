package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TencentCosMediaSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsUploadObjectProcessingAndDeliveryGrantTables() {
        Integer tables = jdbc.queryForObject("""
            select count(distinct lower(table_name))
              from information_schema.tables
             where lower(table_name) in (
               'media_upload_session', 'media_object',
               'media_processing_job', 'media_delivery_grant'
             )
            """, Integer.class);
        Integer uploadColumns = columnCount("media_upload_session",
            "tenant_id", "project_id", "user_id", "session_token", "object_key", "status", "expires_at");
        Integer objectColumns = columnCount("media_object",
            "tenant_id", "project_id", "asset_type", "asset_id", "version_id", "rendition_type",
            "object_key", "mime_type", "file_size", "etag", "storage_class", "status");
        Integer processingColumns = columnCount("media_processing_job",
            "media_object_id", "provider_job_id", "operation", "input_key", "output_key", "status", "attempt_no");
        Integer grantColumns = columnCount("media_delivery_grant",
            "tenant_id", "project_id", "user_id", "resource_type", "resource_id", "version_id",
            "rendition_type", "object_key_hash", "expires_at", "revision");

        assertThat(tables).isEqualTo(4);
        assertThat(uploadColumns).isEqualTo(7);
        assertThat(objectColumns).isEqualTo(12);
        assertThat(processingColumns).isEqualTo(7);
        assertThat(grantColumns).isEqualTo(10);
        assertThat(uniqueConstraintCount("media_delivery_grant")).isOne();
        assertThat(uniqueConstraintCount("media_object")).isEqualTo(2);
        assertThat(columnCount("ai_image_result", "display_path")).isOne();
    }

    private Integer columnCount(String table, String... columns) {
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
        Object[] args = new Object[columns.length + 1];
        args[0] = table;
        System.arraycopy(columns, 0, args, 1, columns.length);
        return jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = ?
               and lower(column_name) in (%s)
            """.formatted(placeholders), Integer.class, args);
    }

    private Integer uniqueConstraintCount(String table) {
        return jdbc.queryForObject("""
            select count(distinct lower(constraint_name))
              from information_schema.table_constraints
             where lower(table_name) = ? and upper(constraint_type) = 'UNIQUE'
            """, Integer.class, table);
    }
}
