package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import java.util.concurrent.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CharacterVariantDeletionConcurrencyTest {
    @Autowired AssetVisualVariantService service;
    @Autowired JdbcTemplate jdbc;

    @Test void concurrentCostumeAndDefaultDeletionKeepReferencesOnRemainingDefault() throws Exception {
        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at) values (99601,99602,99603,'人物','LEAD','CONFIRMED',1,now(),now())");
        var primary=service.create(99602L,99603L,"CHARACTER",99601L,1L,new AssetVisualVariantService.VariantCommand("默认",null,null,"MANUAL","COMPLETED",null,"/primary.png",true));
        var costume=service.create(99602L,99603L,"CHARACTER",99601L,1L,new AssetVisualVariantService.VariantCommand("服装",null,null,"MANUAL","COMPLETED",null,"/costume.png",false));
        var remaining=service.create(99602L,99603L,"CHARACTER",99601L,1L,new AssetVisualVariantService.VariantCommand("保留",null,null,"MANUAL","COMPLETED",null,"/remaining.png",false));
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at) values (99604,99602,99603,1,1,'shot','DRAFT',1,now(),now())");
        jdbc.update("insert into storyboard_asset_reference (tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id,reference_role,sort_order,resolution_status,source_type,locked_by_user,created_by,created_at,updated_at) values (99602,99603,99604,'CHARACTER',99601,?,'VISIBLE',0,'RESOLVED','MANUAL',true,1,now(),now())",costume.id());
        jdbc.update("update storyboard set prompt_document_json=? where id=99604", """
            {"version":1,"nodes":[
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":%d,"assetType":"CHARACTER","assetId":99601},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":%d,"assetType":"CHARACTER","assetId":99601}
            ]}
            """.formatted(primary.id(),costume.id()));
        var executor=Executors.newFixedThreadPool(2);
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var first=executor.submit(()->{ready.countDown();start.await();service.delete(99602L,99603L,primary.id());return null;});
            var second=executor.submit(()->{ready.countDown();start.await();service.delete(99602L,99603L,costume.id());return null;});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select variant_id from storyboard_asset_reference where storyboard_id=99604",Long.class)).isEqualTo(remaining.id());
            assertThat(service.primaryVariant(99602L,99603L,"CHARACTER",99601L).id()).isEqualTo(remaining.id());
            var document=new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                jdbc.queryForObject("select prompt_document_json from storyboard where id=99604",String.class));
            assertThat(document.path("nodes")).hasSize(2);
            for (var node:document.path("nodes")) assertThat(node.path("sourceId").asLong()).isEqualTo(remaining.id());
            assertThat(jdbc.queryForObject("select main_image_url from character_asset where id=99601",String.class))
                .isEqualTo("/remaining.png");
        } finally {start.countDown();executor.shutdownNow();}
    }
}
