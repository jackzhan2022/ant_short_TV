package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AssetNameConcurrencyTest {
    @Autowired AssetNameService names;
    @Autowired AssetVisualVariantService variants;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private static final long TENANT=99701, PROJECT=99702;

    private long seed(long asset,long shot) {
        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at) values (?,99701,99702,'原名','LEAD','CONFIRMED',1,now(),now())",asset);
        long variant=variants.create(TENANT,PROJECT,"CHARACTER",asset,1L,
            new AssetVisualVariantService.VariantCommand("原形态",null,null,"MANUAL","COMPLETED",null,"/image.png",true)).id();
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,prompt_document_json,status,created_by,created_at,updated_at) values (?,99701,99702,1,1,'正文',?,'DRAFT',1,now(),now())",shot,document(variant,"原文"));
        return variant;
    }
    private String document(long variant,String text) {
        return "{\"version\":2,\"nodes\":[{\"type\":\"text\",\"text\":\""+text+"\"},{\"type\":\"mention\",\"mediaType\":\"IMAGE\",\"sourceType\":\"ASSET_VISUAL_VARIANT\",\"sourceId\":"+variant+",\"displayName\":\"原名 / 原形态\"}]}";
    }
    @Test void promptEditAndRenameUseCompatibleLockOrderAndKeepLatestText() throws Exception {
        long variant=seed(99703,99704);
        var editorLocked=new CountDownLatch(1);var renameStarted=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var editor=executor.submit(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.queryForObject("select id from storyboard where id=99704 for update",Long.class);
                editorLocked.countDown();
                try { assertThat(renameStarted.await(5,TimeUnit.SECONDS)).isTrue();Thread.sleep(400); }
                catch(InterruptedException e){throw new RuntimeException(e);}
                // updateStoryboard locks the storyboard before normalizing/locking its variant references.
                jdbc.queryForObject("select id from asset_visual_variant where id=? for update",Long.class,variant);
                jdbc.update("update storyboard set prompt_document_json=? where id=99704",document(variant,"并发编辑的新正文"));
                return null;
            }));
            assertThat(editorLocked.await(5,TimeUnit.SECONDS)).isTrue();
            var rename=executor.submit(()->{renameStarted.countDown();names.renameVariant(TENANT,PROJECT,variant,"新形态");});
            editor.get(15,TimeUnit.SECONDS);rename.get(15,TimeUnit.SECONDS);
            var tree=new ObjectMapper().readTree(jdbc.queryForObject("select prompt_document_json from storyboard where id=99704",String.class));
            assertThat(tree.path("nodes").get(0).path("text").asText()).isEqualTo("并发编辑的新正文");
            assertThat(tree.path("nodes").get(1).path("displayName").asText()).isEqualTo("原名 / 新形态");
        } finally {renameStarted.countDown();executor.shutdownNow();}
    }
    @Test void concurrentAssetAndVariantRenameKeepBothNames() throws Exception {
        long variant=seed(99705,99706);var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var asset=executor.submit(()->{start.await();names.renameAsset(TENANT,PROJECT,"CHARACTER",99705,"新资产");return null;});
            var shape=executor.submit(()->{start.await();names.renameVariant(TENANT,PROJECT,variant,"新形态");return null;});
            start.countDown();asset.get(15,TimeUnit.SECONDS);shape.get(15,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=99706",String.class)).contains("新资产 / 新形态");
        } finally {start.countDown();executor.shutdownNow();}
    }
    @Test void invalidPromptRollsBackNameAndAllEarlierLabelChanges() {
        long variant=seed(99707,99708);
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,prompt_document_json,status,created_by,created_at,updated_at) values (99709,99701,99702,1,2,'正文',?,'DRAFT',1,now(),now())","{broken:"+variant);
        String before=jdbc.queryForObject("select prompt_document_json from storyboard where id=99708",String.class);
        assertThatThrownBy(()->names.renameVariant(TENANT,PROJECT,variant,"不应保存")).isInstanceOf(com.antshorttv.common.BusinessException.class).hasMessageContaining("格式异常");
        assertThat(jdbc.queryForObject("select name from asset_visual_variant where id=?",String.class,variant)).isEqualTo("原形态");
        assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=99708",String.class)).isEqualTo(before);
    }
}
