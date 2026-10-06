package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StoryboardAssetReferenceConcurrencyTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private StoryboardAssetReferenceRepository repository;

    @Test
    void saveWaitsForImageCompletionAndPersistsReadyStatus() throws Exception {
        ProjectPermissionGuard guard = mock(ProjectPermissionGuard.class);
        when(guard.require(9901L, 9902L, "STORYBOARD:EDIT"))
            .thenReturn(new TenantContext(1L, 9901L, 1L, "OWNER"));
        StoryboardAssetReferenceService service = new StoryboardAssetReferenceService(guard, jdbc, repository);
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(ignored -> {
            jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9901,'race-a','A','TEAM','ACTIVE',now(),now())");
            jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9902,9901,'A','race-a',1,'ACTIVE',1,now(),now())");
            jdbc.update("insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at) values (9910,9901,9902,'林夏','LEAD','CONFIRMED',1,now(),now())");
            jdbc.update("insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,is_primary,created_by,created_at,updated_at) values (9920,9901,9902,'CHARACTER',9910,'主形象','MANUAL','NOT_STARTED',true,1,now(),now())");
            jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at) values (9930,9901,9902,1,1,'角色入场','DRAFT',1,now(),now())");
        });
        CountDownLatch imageLocked = new CountDownLatch(1);
        CountDownLatch finishImage = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> image = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(ignored -> {
                jdbc.queryForList("select id from asset_visual_variant where id=9920 for update");
                imageLocked.countDown();
                try {
                    if (!finishImage.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("image release timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                jdbc.update("update asset_visual_variant set generation_status='COMPLETED', current_image_url='/ready.png' where id=9920");
            }), executor);
            assertThat(imageLocked.await(5, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<String> saved = CompletableFuture.supplyAsync(() -> transaction.execute(ignored ->
                service.replace(9901L, 9902L, 9930L, new ReplaceStoryboardAssetReferencesRequest(List.of(
                    new StoryboardAssetReferenceCommand("CHARACTER", 9910L, 9920L, "VISIBLE", 0, null)
                ))).get(0).resolutionStatus()), executor);
            assertThatThrownBy(() -> saved.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
            finishImage.countDown();
            image.get(10, TimeUnit.SECONDS);
            assertThat(saved.get(10, TimeUnit.SECONDS)).isEqualTo("RESOLVED");
        } finally {
            finishImage.countDown();
            executor.shutdownNow();
        }
    }
}
