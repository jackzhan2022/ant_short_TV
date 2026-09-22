package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InspirationGalleryManagementMigrationTest {
    @Test
    void migrationAddsManagementFieldsAndPermission() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/db/migration/V121__inspiration_gallery_management.sql"
        )) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                .contains("prompt_text")
                .contains("tags_json")
                .contains("publish_status")
                .contains("source_type")
                .contains("deleted_at")
                .contains("PLATFORM_INSPIRATION_MANAGE")
                .contains("PLATFORM_ADMIN")
                .contains("PUBLISHED")
                .contains("IMPORTED");
        }
    }
}
