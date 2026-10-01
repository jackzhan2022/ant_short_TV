package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.qcloud.cos.COS;
import com.qcloud.cos.transfer.TransferManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class FailedDisplayOutputCleanupPersistenceTest {
    private static final String ORIGINAL = "materials/11/22/images/202609/42/v1/original.png";
    private static final String DISPLAY = "materials/11/22/images/202609/42/v1/derived/display.png";
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 10, 2, 12, 0).minusDays(14);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MediaProcessingJobCoordinator coordinator;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private FailedDisplayOutputCleanupService cleanup;
    @Autowired private FailedDisplayOutputCleanupScheduler scheduler;
    @SpyBean private FailedDisplayOutputCleanupStore cleanupStore;
    @MockBean private COS cos;
    @MockBean private TransferManager transfers;
    @MockBean private ObjectStorageService storage;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from media_processing_job");
        jdbc.update("delete from media_object");
    }

    @Test
    void deletesExactlyOnceAtTheFourteenDayBoundary() throws Exception {
        long jobId = failedDisplay(CUTOFF);

        assertThat(clean(jobId)).isTrue();
        assertThat(clean(jobId)).isFalse();

        verify(storage, times(1)).delete(DISPLAY);
        verifyNoMoreInteractions(storage);
        assertThat(jdbc.queryForObject("""
            select count(*) from media_processing_output_cleanup where job_id = ? and attempt_no = 1
            """, Integer.class, jobId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from media_processing_job where id = ?",
            String.class, jobId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from media_object where object_key = ?",
            String.class, ORIGINAL)).isEqualTo("READY");
    }

    @Test
    void retainsOutputUntilTheWholeRetentionPeriodHasElapsed() throws Exception {
        long jobId = failedDisplay(CUTOFF.plusSeconds(1));

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
        assertThat(markerCount()).isZero();
    }

    @Test
    void acceptsTheAiImageProducerPhysicalVersionWithItsRegisteredLogicalVersion() {
        long jobId = failedDisplay(CUTOFF);
        ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();
        String original = keys.projectOriginal(11L, 22L, "images", 42L, "task-77-0",
            LocalDate.of(2026, 9, 17), "png");
        String display = keys.rendition(original, "display", "png");
        rebindKeys(jobId, original, display);
        jdbc.update("update media_object set version_id = 'result-42'");

        assertThat(clean(jobId)).isTrue();

        verify(storage).delete(display);
        verifyNoMoreInteractions(storage);
        assertThat(jdbc.queryForObject("select object_key from media_object where rendition_type = 'ORIGINAL'",
            String.class)).isEqualTo(original);
        assertThat(jdbc.queryForObject("select version_id from media_object where rendition_type = 'ORIGINAL'",
            String.class)).isEqualTo("result-42");
    }

    @ParameterizedTest
    @ValueSource(strings = {"style", "inspiration", "cover"})
    void acceptsExistingPlatformImageAndVideoCoverProducerShapes(String producer) {
        long jobId = failedDisplay(CUTOFF);
        ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();
        boolean cover = "cover".equals(producer);
        String namespace = "style".equals(producer) ? "style_library" : "inspiration_creation";
        String assetType = "style".equals(producer) ? "STYLE_LIBRARY" : "INSPIRATION_CREATION";
        String mimeType = cover ? "image/jpeg" : "image/png";
        String extension = cover ? "jpg" : "png";
        String original = keys.tenantOriginal(0L, namespace, 42L, "external-42",
            LocalDate.of(2026, 9, 17), extension);
        if (cover) original = original.replace("/original.jpg", "/cover/original.jpg");
        String display = keys.rendition(original, "display", extension);
        rebindKeys(jobId, original, display);
        jdbc.update("""
            update media_object set tenant_id = 0, project_id = null, asset_type = ?,
                version_id = 'external-42', mime_type = ?
            """, assetType, mimeType);
        jdbc.update("update media_processing_job set tenant_id = 0, project_id = null where id = ?", jobId);

        assertThat(clean(jobId)).isTrue();

        verify(storage).delete(display);
        verifyNoMoreInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "materials/12/22/images/202609/42/task-77-0/original.png",
        "materials/11/23/images/202609/42/task-77-0/original.png",
        "materials/11/22/images/202609/43/task-77-0/original.png",
        "materials/11/22/images/202609/42/task-77-0/derived/original.png",
        "materials/11/22/uploads/202609/42/task-77-0/original.png",
        "materials/11/22/images/202609/42/task-77-0/extra/original.png",
        "legacy/11/22/images/202609/42/task-77-0/original.png"
    })
    void rejectsNonCanonicalRegisteredOriginalKeysEvenWhenThePlannerOutputMatches(String original) {
        long jobId = failedDisplay(CUTOFF);
        String display = new ObjectStorageKeyFactory().rendition(original, "display", "png");
        rebindKeys(jobId, original, display);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "SUBMITTING", "SUBMITTED", "RUNNING", "SUCCEEDED", "CANCELED"})
    void neverDeletesNonFailedJobOutputs(String status) throws Exception {
        long jobId = failedDisplay(CUTOFF);
        jdbc.update("update media_processing_job set status = ? where id = ?", status, jobId);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "PENDING", "SUBMITTED", "RETIRED"})
    void neverDeletesNonFailedMediaObjects(String status) throws Exception {
        long jobId = failedDisplay(CUTOFF);
        jdbc.update("update media_object set status = ? where object_key = ?", status, DISPLAY);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "legacy/42/v1/derived/display.png",
        "uploads/11/session/derived/display.png",
        "materials/11/22/images/202609/42/v1/derived/display-small.png",
        "materials/11/22/images/202609/43/v1/derived/display.png",
        "materials/11/22/images/202609/42/v1/derived/display.jpg",
        "materials/11/22/images/202609/42/v1/other/original.png"
    })
    void rejectsMatchingJobAndMediaKeysOutsideTheExactDisplayContract(String output) throws Exception {
        long jobId = failedDisplay(CUTOFF);
        jdbc.update("update media_object set object_key = ? where object_key = ?", output, DISPLAY);
        jdbc.update("update media_processing_job set output_key = ? where id = ?", output, jobId);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @Test
    void neverDeletesAnOriginalEvenWhenTheJobClaimsItAsItsFailedOutput() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        long originalId = jdbc.queryForObject("select id from media_object where object_key = ?", Long.class, ORIGINAL);
        jdbc.update("update media_object set status = 'FAILED' where id = ?", originalId);
        jdbc.update("update media_processing_job set media_object_id = ?, output_key = ? where id = ?",
            originalId, ORIGINAL, jobId);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "delete from media_object where rendition_type = 'ORIGINAL'",
        "update media_object set status = 'FAILED' where rendition_type = 'ORIGINAL'",
        "update media_object set etag = null where rendition_type = 'ORIGINAL'",
        "update media_object set width = null where rendition_type = 'ORIGINAL'",
        "update media_object set project_id = 23 where rendition_type = 'ORIGINAL'",
        "update media_object set version_id = 'another-logical-version' where rendition_type = 'ORIGINAL'",
        "update media_processing_job set input_key = 'materials/11/22/images/202609/43/v1/original.png'",
        "update media_processing_job set output_key = 'materials/11/22/images/202609/43/v1/derived/display.png'",
        "update media_processing_job set tenant_id = 12",
        "update media_processing_job set completed_at = null",
        "update media_processing_job set operation = 'OTHER_OPERATION'"
    })
    void failsClosedOnMissingOrMismatchedPersistedEvidence(String mutation) throws Exception {
        long jobId = failedDisplay(CUTOFF);
        jdbc.update(mutation);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @Test
    void excludesTheCompletedUploadSessionNamespace() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        String input = "materials/11/22/uploads/202609/42/v1/original.png";
        String output = input.replace("/original.png", "/derived/display.png");
        jdbc.update("update media_object set object_key = ? where object_key = ?", input, ORIGINAL);
        jdbc.update("update media_object set object_key = ? where object_key = ?", output, DISPLAY);
        jdbc.update("update media_processing_job set input_key = ?, output_key = ? where id = ?", input, output, jobId);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @Test
    void retriesCloudFailureWithoutPersistingAFalseCleanedMarker() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        doThrow(new IllegalStateException("COS unavailable")).when(storage).delete(DISPLAY);

        assertThatThrownBy(() -> clean(jobId)).hasMessage("COS unavailable");
        assertThat(markerCount()).isZero();
        doNothing().when(storage).delete(DISPLAY);

        assertThat(clean(jobId)).isTrue();
        verify(storage, times(2)).delete(DISPLAY);
        assertThat(markerCount()).isEqualTo(1);
    }

    @Test
    void rechecksASelectedCandidateAfterItsMediaStateChanges() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        assertThat(jdbc.queryForObject("select id from media_processing_job where status = 'FAILED'",
            Long.class)).isEqualTo(jobId);
        jdbc.update("update media_object set status = 'READY' where object_key = ?", DISPLAY);

        assertThat(clean(jobId)).isFalse();

        verifyNoInteractions(storage);
    }

    @Test
    void aNewFailedAttemptIsNotSuppressedByThePreviousAttemptMarker() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        assertThat(clean(jobId)).isTrue();
        PreparedMediaProcessingJob retry = coordinator.prepareImageDisplay(
            new SubmitImageDisplayJob(new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 42L, "v1"),
                ORIGINAL, "image/png", "INTELLIGENT_TIERING", "ai-image-result:42"),
            new ImageDisplayRenditionPlan(DISPLAY, "image/png", "imageSlim"), "unused-new-token-hash"
        );
        assertThat(retry.shouldSubmit()).isTrue();
        coordinator.failSubmission(retry, "Retry failed");
        jdbc.update("update media_processing_job set completed_at = ? where id = ?", CUTOFF, jobId);

        assertThat(clean(jobId)).isTrue();

        verify(storage, times(2)).delete(DISPLAY);
        assertThat(markerCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select attempt_no from media_processing_job where id = ?",
            Integer.class, jobId)).isEqualTo(2);
    }

    @Test
    void holdsTheJobRowLockThroughCloudDeleteAndMarkerCommit() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch allowDelete = new CountDownLatch(1);
        CountDownLatch retryStarted = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            deleting.countDown();
            assertThat(allowDelete.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(storage).delete(DISPLAY);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var cleanup = executor.submit(() -> clean(jobId));
            assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
            var retry = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                retryStarted.countDown();
                return jdbc.update("update media_processing_job set status = 'SUBMITTING' where id = ?", jobId);
            }));
            assertThat(retryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> retry.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            allowDelete.countDown();

            assertThat(cleanup.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(retry.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(markerCount()).isEqualTo(1);
        } finally {
            allowDelete.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void letsAnAlreadyClaimedCoordinatorRetryCompleteWithoutAJobMetadataDeadlock() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        CountDownLatch displayLocked = new CountDownLatch(1);
        CountDownLatch allowRetry = new CountDownLatch(1);
        CountDownLatch cleanupLockedJob = new CountDownLatch(1);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            cleanupLockedJob.countDown();
            return result;
        }).when(cleanupStore).jobForUpdate(jobId);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var retry = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("select id from media_object where object_key = ? for update", Long.class, DISPLAY);
                displayLocked.countDown();
                try {
                    if (!allowRetry.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Retry release timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return coordinator.prepareImageDisplay(
                    new SubmitImageDisplayJob(new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 42L, "v1"),
                        ORIGINAL, "image/png", "INTELLIGENT_TIERING", "ai-image-result:42"),
                    new ImageDisplayRenditionPlan(DISPLAY, "image/png", "imageSlim"), "unused-new-token-hash"
                );
            }));
            assertThat(displayLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var cleanup = executor.submit(() -> clean(jobId));
            cleanupLockedJob.await(1, TimeUnit.SECONDS);
            allowRetry.countDown();

            assertThatCode(() -> retry.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
            assertThat(retry.get(5, TimeUnit.SECONDS).shouldSubmit()).isTrue();
            assertThatCode(() -> cleanup.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
            boolean cleaned = cleanup.get(5, TimeUnit.SECONDS);
            if (cleaned) verify(storage).delete(DISPLAY); else verifyNoInteractions(storage);
            assertThat(markerCount()).isEqualTo(cleaned ? 1 : 0);
            assertThat(jdbc.queryForObject("select attempt_no from media_processing_job where id = ?",
                Integer.class, jobId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select status from media_processing_job where id = ?",
                String.class, jobId)).isEqualTo("SUBMITTING");
        } finally {
            allowRetry.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void twoWorkersDeleteOneFailedAttemptOnlyOnce() throws Exception {
        long jobId = failedDisplay(CUTOFF);
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch allowDelete = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(call -> {
            deleting.countDown();
            assertThat(allowDelete.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(storage).delete(DISPLAY);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> clean(jobId));
            assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return clean(jobId);
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            allowDelete.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
            verify(storage, times(1)).delete(DISPLAY);
            assertThat(markerCount()).isEqualTo(1);
        } finally {
            allowDelete.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void schedulerRevisitsEarlierFailureDespiteFullBatchesOfNewArrivals() {
        FailedDisplayOutputCleanupScheduler scanner = fixedScheduler();
        long olderJob = failedDisplay(CUTOFF);
        for (long asset = 43; asset < 142; asset++) failedDisplay(asset, CUTOFF);
        doThrow(new IllegalStateException("COS unavailable")).when(storage).delete(DISPLAY);
        assertThat(scanner.cleanupExpired(100)).isEqualTo(99);
        doNothing().when(storage).delete(DISPLAY);

        for (int arrival = 0; arrival < 3; arrival++) {
            for (long asset = 142L + arrival * 100; asset < 242L + arrival * 100; asset++) {
                failedDisplay(asset, CUTOFF);
            }
            scanner.cleanupExpired(100);
        }

        verify(storage, times(2)).delete(DISPLAY);
        assertThat(jdbc.queryForObject("select count(*) from media_processing_output_cleanup where job_id = ?",
            Integer.class, olderJob)).isEqualTo(1);
    }

    @Test
    void schedulerRevisitsLowerOldAttemptsThatBecomeEligibleDuringAContinuousScan() {
        FailedDisplayOutputCleanupScheduler scanner = fixedScheduler();
        long lowerJob = failedDisplay(CUTOFF);
        assertThat(clean(lowerJob)).isTrue();
        failedDisplay(43L, CUTOFF);
        failedDisplay(44L, CUTOFF);
        assertThat(scanner.cleanupExpired(1)).isEqualTo(1);
        PreparedMediaProcessingJob retry = coordinator.prepareImageDisplay(
            new SubmitImageDisplayJob(new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 42L, "v1"),
                ORIGINAL, "image/png", "INTELLIGENT_TIERING", "ai-image-result:42"),
            new ImageDisplayRenditionPlan(DISPLAY, "image/png", "imageSlim"), "unused-new-token-hash"
        );
        coordinator.failSubmission(retry, "Retry failed");
        jdbc.update("update media_processing_job set completed_at = ? where id = ?", CUTOFF, lowerJob);
        failedDisplay(45L, CUTOFF);
        scanner.cleanupExpired(1);
        failedDisplay(46L, CUTOFF);
        scanner.cleanupExpired(1);

        verify(storage, times(2)).delete(DISPLAY);
        assertThat(jdbc.queryForObject("""
            select count(*) from media_processing_output_cleanup where job_id = ? and attempt_no = 2
            """, Integer.class, lowerJob)).isEqualTo(1);
    }

    @Test
    void schedulerProcessesBoundedBatchesAndSkipsCompletedMarkers() throws Exception {
        LocalDateTime expired = LocalDateTime.now().minusDays(15);
        failedDisplay(42L, expired);
        failedDisplay(43L, expired);
        failedDisplay(44L, LocalDateTime.now().minusDays(13));

        assertThat(cleanupExpired(1)).isEqualTo(1);
        assertThat(cleanupExpired(1)).isEqualTo(1);
        assertThat(cleanupExpired(1)).isZero();

        verify(storage).delete(DISPLAY);
        verify(storage).delete(DISPLAY.replace("/42/", "/43/"));
        verifyNoMoreInteractions(storage);
        assertThat(markerCount()).isEqualTo(2);
    }

    @Test
    void schedulerContinuesAfterCloudFailureAndRevisitsItOnTheNextPass() throws Exception {
        LocalDateTime expired = LocalDateTime.now().minusDays(15);
        failedDisplay(42L, expired);
        failedDisplay(43L, expired);
        doThrow(new IllegalStateException("COS unavailable")).when(storage).delete(DISPLAY);

        assertThat(cleanupExpired(2)).isEqualTo(1);
        doNothing().when(storage).delete(DISPLAY);
        assertThat(cleanupExpired(2)).isEqualTo(1);

        verify(storage, times(2)).delete(DISPLAY);
        verify(storage).delete(DISPLAY.replace("/42/", "/43/"));
        assertThat(markerCount()).isEqualTo(2);
    }

    @Test
    void schedulerRejectsUnboundedBatchSizes() {
        assertThatThrownBy(() -> cleanupExpired(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cleanupExpired(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private int cleanupExpired(int limit) {
        return scheduler.cleanupExpired(limit);
    }

    private FailedDisplayOutputCleanupScheduler fixedScheduler() {
        return new FailedDisplayOutputCleanupScheduler(cleanupStore, cleanup,
            Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC));
    }

    private void rebindKeys(long jobId, String original, String display) {
        jdbc.update("update media_object set object_key = ? where object_key = ?", original, ORIGINAL);
        jdbc.update("update media_object set object_key = ? where object_key = ?", display, DISPLAY);
        jdbc.update("update media_processing_job set input_key = ?, output_key = ? where id = ?",
            original, display, jobId);
    }

    private boolean clean(long jobId) {
        return cleanup.clean(jobId, CUTOFF);
    }

    private int markerCount() {
        return jdbc.queryForObject("select count(*) from media_processing_output_cleanup", Integer.class);
    }

    private long failedDisplay(LocalDateTime completedAt) {
        return failedDisplay(42L, completedAt);
    }

    private long failedDisplay(long assetId, LocalDateTime completedAt) {
        String original = ORIGINAL.replace("/42/", "/" + assetId + "/");
        String display = DISPLAY.replace("/42/", "/" + assetId + "/");
        insertMedia(assetId, "ORIGINAL", original, "READY", 1234L);
        long displayId = insertMedia(assetId, "DISPLAY_IMAGE_SLIM", display, "FAILED", 0L);
        jdbc.update("""
            insert into media_processing_job (
                tenant_id, project_id, media_object_id, provider_job_id, operation, input_key,
                output_key, status, attempt_no, callback_token_hash, correlation_data,
                completed_at, created_at, updated_at
            ) values (11, 22, ?, ?, 'DISPLAY_IMAGE_SLIM', ?, ?, 'FAILED', 1,
                ?, ?, ?, ?, ?)
            """, displayId, "failed-cleanup-job-" + assetId, original, display,
            "cleanup-test-token-hash-" + assetId, "ai-image-result:" + assetId,
            completedAt, completedAt, completedAt);
        return jdbc.queryForObject("select id from media_processing_job where output_key = ?",
            Long.class, display);
    }

    private long insertMedia(long assetId, String rendition, String key, String status, long size) {
        jdbc.update("""
            insert into media_object (
                tenant_id, project_id, asset_type, asset_id, version_id, rendition_type,
                object_key, mime_type, file_size, etag, storage_class, width, height, status,
                created_at, updated_at
            ) values (11, 22, 'AI_IMAGE_RESULT', ?, 'v1', ?, ?, 'image/png', ?, 'source-etag',
                'INTELLIGENT_TIERING', 100, 80, ?, ?, ?)
            """, assetId, rendition, key, size, status, CUTOFF, CUTOFF);
        return jdbc.queryForObject("select id from media_object where object_key = ?", Long.class, key);
    }
}
