package com.antshorttv.aiimage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AssetImageBatchScheduler {
    private final JdbcTemplate jdbc;
    private final AiImageTaskService imageTaskService;

    AssetImageBatchScheduler(JdbcTemplate jdbc, AiImageTaskService imageTaskService) {
        this.jdbc = jdbc;
        this.imageTaskService = imageTaskService;
    }

    @Scheduled(fixedDelayString = "${asset.image.batch.scheduler.fixed-delay-ms:2000}")
    void tick() {
        recoverStaleDispatches();
        refreshTaskItems();
        releaseDependencies();
        dispatchReadyItems(null, 20);
        refreshBatchStatuses();
    }

    void dispatchBatch(Long batchId) {
        refreshTaskItems();
        releaseDependencies();
        while (dispatchReadyItems(batchId, 500) > 0) {
            // Drain every directly executable item before returning the submit response.
        }
        refreshBatchStatuses();
    }

    private void refreshTaskItems() {
        List<Map<String, Object>> items = jdbc.queryForList("""
            select item.id, item.task_id, task.status, task.error_message
              from asset_image_batch_item item
              join ai_image_task task on task.id = item.task_id
             where item.status = 'RUNNING'
            """);
        for (Map<String, Object> item : items) {
            String taskStatus = text(item.get("status"));
            if ("SUCCESS".equals(taskStatus)) {
                updateItem(number(item.get("id")), "SUCCEEDED", null);
            } else if ("FAILED".equals(taskStatus) || "CANCELED".equals(taskStatus)) {
                updateItem(number(item.get("id")), "FAILED", text(item.get("error_message")));
            }
        }
    }

    private void releaseDependencies() {
        List<Map<String, Object>> items = jdbc.queryForList("""
            select waiting.id, dependency.status dependency_status
              from asset_image_batch_item waiting
              join asset_image_batch_item dependency on dependency.id = waiting.dependency_item_id
             where waiting.status = 'WAITING_DEPENDENCY'
            """);
        for (Map<String, Object> item : items) {
            Long itemId = number(item.get("id"));
            String dependencyStatus = text(item.get("dependency_status"));
            if ("SUCCEEDED".equals(dependencyStatus)) {
                updateItem(itemId, "PENDING", null);
            } else if ("FAILED".equals(dependencyStatus) || "SKIPPED".equals(dependencyStatus)) {
                updateItem(itemId, "SKIPPED", "主形象生成失败");
            }
        }
    }

    private int dispatchReadyItems(Long batchId, int limit) {
        String sql = """
            select item.id, item.tenant_id, item.project_id, item.variant_id,
                   item.asset_type, batch.model_id, batch.aspect_ratio, batch.image_count,
                   batch.created_by
              from asset_image_batch_item item
              join asset_image_batch batch on batch.id = item.batch_id
             where item.status = 'PENDING' and item.task_id is null
            """ + (batchId == null ? "" : " and item.batch_id = ?")
            + " order by item.id limit " + Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> items = batchId == null
            ? jdbc.queryForList(sql)
            : jdbc.queryForList(sql, batchId);
        int claimedItems = 0;
        for (Map<String, Object> item : items) {
            Long itemId = number(item.get("id"));
            int claimed = jdbc.update("""
                update asset_image_batch_item
                   set status = 'DISPATCHING', updated_at = now()
                 where id = ? and status = 'PENDING' and task_id is null
                """, itemId);
            if (claimed != 1) continue;
            claimedItems += 1;
            try {
                AiImageTaskResponse task = imageTaskService.createForBatch(
                    number(item.get("tenant_id")),
                    number(item.get("project_id")),
                    number(item.get("created_by")),
                    new CreateAiImageTaskRequest(
                        text(item.get("asset_type")), "VISUAL_VARIANT",
                        number(item.get("variant_id")), number(item.get("model_id")),
                        "批量资产图生成", null, List.of(), text(item.get("aspect_ratio")),
                        ((Number) item.get("image_count")).intValue(), null, "STANDARD", null),
                    "asset-image-batch-item:" + itemId,
                    "asset-image-batch-item:" + itemId);
                jdbc.update("""
                    update asset_image_batch_item
                       set task_id = ?, status = 'RUNNING', error_message = null, updated_at = now()
                     where id = ? and status = 'DISPATCHING' and task_id is null
                    """, task.id(), itemId);
            } catch (RuntimeException exception) {
                updateItem(itemId, "FAILED", bounded(exception.getMessage()));
            }
        }
        return claimedItems;
    }

    private void refreshBatchStatuses() {
        List<Long> batchIds = jdbc.queryForList("""
            select id from asset_image_batch where status in ('PENDING', 'RUNNING')
            """, Long.class);
        for (Long batchId : batchIds) {
            Map<String, Object> counts = jdbc.queryForMap("""
                select
                  sum(case when status in ('PENDING', 'WAITING_DEPENDENCY') then 1 else 0 end) pending_count,
                  sum(case when status in ('RUNNING', 'DISPATCHING') then 1 else 0 end) running_count,
                  sum(case when status = 'SUCCEEDED' then 1 else 0 end) succeeded_count,
                  sum(case when status = 'FAILED' then 1 else 0 end) failed_count,
                  sum(case when status = 'SKIPPED' then 1 else 0 end) skipped_count
                from asset_image_batch_item where batch_id = ?
                """, batchId);
            String status = AssetImageBatchService.batchStatus(
                integer(counts.get("pending_count")), integer(counts.get("running_count")),
                integer(counts.get("succeeded_count")), integer(counts.get("failed_count")),
                integer(counts.get("skipped_count")));
            jdbc.update("update asset_image_batch set status = ?, updated_at = now() where id = ?", status, batchId);
        }
    }

    private void updateItem(Long itemId, String status, String error) {
        jdbc.update("""
            update asset_image_batch_item
               set status = ?, error_message = ?, updated_at = now()
             where id = ?
            """, status, error, itemId);
    }

    private void recoverStaleDispatches() {
        jdbc.update("""
            update asset_image_batch_item
               set status = 'PENDING', updated_at = now()
             where status = 'DISPATCHING' and task_id is null
               and updated_at < ?
            """, LocalDateTime.now().minusMinutes(5));
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "资产图任务提交失败";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
