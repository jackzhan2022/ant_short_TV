package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MediaProcessingJobCoordinatorPersistenceTest {
    @Autowired private MediaProcessingJobCoordinator coordinator;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from media_processing_job");
        jdbc.update("delete from media_object");
    }

    @Test
    void resetsFailedRetryColumnsToSqlNullWhileReusingCallbackTokenAndIncrementingAttempt() {
        String inputKey = "materials/11/22/images/202609/42/v1/original.png";
        String outputKey = "materials/11/22/images/202609/42/v1/derived/display.png";
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 10, 0);
        jdbc.update("""
            insert into media_object (
              tenant_id, project_id, asset_type, asset_id, version_id, rendition_type,
              object_key, mime_type, file_size, storage_class, status, error_message,
              created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, 11L, 22L, "AI_IMAGE_RESULT", 42L, "v1", "DISPLAY_IMAGE_SLIM",
            outputKey, "image/png", 0L, "STANDARD", "FAILED", "old media error",
            createdAt, createdAt);
        Long mediaObjectId = jdbc.queryForObject(
            "select id from media_object where object_key = ?", Long.class, outputKey
        );
        jdbc.update("""
            insert into media_processing_job (
              tenant_id, project_id, media_object_id, provider_job_id, queue_id, operation,
              input_key, output_key, callback_token_hash, correlation_data, status, attempt_no,
              error_code, error_message, submitted_at, completed_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, 11L, 22L, mediaObjectId, "old-provider-job", "old-queue",
            "DISPLAY_IMAGE_SLIM", inputKey, outputKey, "old-token-hash",
            "ai-image-result:42", "FAILED", 3, "OLD_FAILURE", "old job error",
            createdAt.plusMinutes(1), createdAt.plusMinutes(2), createdAt, createdAt.plusMinutes(2));

        PreparedMediaProcessingJob prepared = coordinator.prepareImageDisplay(
            new SubmitImageDisplayJob(
                new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 42L, "v1"),
                inputKey, "image/png", "STANDARD", "ai-image-result:42"
            ),
            new ImageDisplayRenditionPlan(outputKey, "image/png", "imageSlim"),
            "replacement-token-hash"
        );

        Map<String, Object> row = jdbc.queryForMap(
            "select * from media_processing_job where output_key = ?", outputKey
        );
        assertThat(prepared.callbackTokenHash()).isEqualTo("old-token-hash");
        assertThat(prepared.shouldSubmit()).isTrue();
        assertThat(row.get("callback_token_hash")).isEqualTo("old-token-hash");
        assertThat(row.get("attempt_no")).isEqualTo(4);
        assertThat(row.get("status")).isEqualTo("SUBMITTING");
        assertThat(row.get("provider_job_id")).isNull();
        assertThat(row.get("queue_id")).isNull();
        assertThat(row.get("error_code")).isNull();
        assertThat(row.get("error_message")).isNull();
        assertThat(row.get("submitted_at")).isNull();
        assertThat(row.get("completed_at")).isNull();
    }
}
