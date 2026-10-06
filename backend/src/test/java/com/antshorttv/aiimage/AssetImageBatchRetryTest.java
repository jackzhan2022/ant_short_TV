package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetImageBatchRetryTest {
    @Autowired private AssetImageBatchService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ProjectPermissionGuard permissionGuard;
    @MockBean private AiImageTaskService imageTaskService;

    @BeforeEach
    void seedMixedBatch() {
        when(permissionGuard.require(anyLong(), anyLong(), anyString()))
            .thenReturn(new TenantContext(1L, 9901L, 1L, "OWNER"));
        jdbc.update("""
            insert into character_asset
              (id,tenant_id,project_id,name,role_type,prompt,status,created_by,created_at,updated_at)
            values (9910,9901,9902,'林夏','LEAD','角色主形象','CONFIRMED',1,now(),now())
            """);
        jdbc.update("""
            insert into asset_visual_variant
              (id,tenant_id,project_id,asset_type,asset_id,name,prompt,source_type,
               generation_status,current_image_url,is_primary,created_by,created_at,updated_at)
            values
              (9920,9901,9902,'CHARACTER',9910,'主形象','角色主形象','MANUAL',
               'COMPLETED','/done.png',true,1,now(),now()),
              (9921,9901,9902,'CHARACTER',9910,'晚宴礼服','黑色礼服','MANUAL',
               'FAILED',null,false,1,now(),now())
            """);
        jdbc.update("""
            insert into asset_image_batch
              (id,tenant_id,project_id,asset_type,generation_mode,aspect_ratio,image_count,
               status,idempotency_key,creation_token,created_by,created_at,updated_at)
            values (9930,9901,9902,'CHARACTER','ALL','16:9',1,'COMPLETED_WITH_FAILURES',
                    'source-batch','source-token',1,now(),now())
            """);
        jdbc.update("""
            insert into asset_image_batch_item
              (batch_id,tenant_id,project_id,asset_type,asset_id,variant_id,variant_name,
               stage,status,error_message,created_at,updated_at)
            values
              (9930,9901,9902,'CHARACTER',9910,9920,'主形象','PRIMARY','SUCCEEDED',null,now(),now()),
              (9930,9901,9902,'CHARACTER',9910,9921,'晚宴礼服','DIRECT','FAILED','生成超时',now(),now())
            """);
    }

    @Test
    void retryCreatesOneItemForTheFailedVariant() {
        AssetImageBatchResponse retry = service.retryFailed(9901L, 9902L, 9930L, "retry-batch");

        assertThat(retry.id()).isNotEqualTo(9930L);
        assertThat(retry.items()).singleElement().satisfies(item -> {
            assertThat(item.variantId()).isEqualTo(9921L);
            assertThat(item.status()).isEqualTo("PENDING");
        });
        assertThat(service.retryFailed(9901L, 9902L, 9930L, "retry-batch").id())
            .isEqualTo(retry.id());
    }
}
