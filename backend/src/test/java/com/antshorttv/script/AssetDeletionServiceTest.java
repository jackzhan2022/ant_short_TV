package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class AssetDeletionServiceTest {
    private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private AssetDeletionService service;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        for (String table : new String[]{"character_asset", "scene_asset", "prop_asset"}) {
            jdbc.execute("create table " + table + " (id bigint,tenant_id bigint,project_id bigint,name varchar(100),"
                + "merge_target_id bigint,deleted_at timestamp,updated_at timestamp)");
        }
        jdbc.execute("create table asset_visual_variant (id bigint,tenant_id bigint,project_id bigint,asset_type varchar(32),asset_id bigint,current_image_url varchar(100))");
        jdbc.execute("create table storyboard (id bigint,tenant_id bigint,project_id bigint,prompt_document_json clob,"
            + "props varchar(100),video_prompt varchar(100),current_video_url varchar(100),deleted_at timestamp,updated_at timestamp)");
        jdbc.execute("create table storyboard_asset_reference (id bigint,tenant_id bigint,project_id bigint,storyboard_id bigint,"
            + "asset_type varchar(32),asset_id bigint,variant_id bigint,retired_at timestamp,updated_at timestamp)");
        ProxyFactory proxy = new ProxyFactory(new AssetDeletionService(jdbc, json));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        service = (AssetDeletionService) proxy.getProxy();
    }

    private JsonNode document(String type) throws Exception {
        return json.readTree("""
            {"version":2,"nodes":[{"type":"text","text":"手持"},
             {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","assetType":"%s","assetId":10,"sourceId":20,"variantId":20,"displayName":"香烟"},
             {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":20,"displayName":"另一处香烟"},
             {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","assetType":"PROP","assetId":11,"sourceId":21,"displayName":"手机"},
             {"type":"mention","mediaType":"VIDEO","sourceType":"VIDEO_RESULT","sourceId":20,"displayName":"已生成视频"}]}
            """.formatted(type));
    }

    private void seed(String type, String table) throws Exception {
        jdbc.update("insert into " + table + "(id,tenant_id,project_id,name) values(10,1,2,'香烟')");
        jdbc.update("insert into asset_visual_variant values(20,1,2,?,10,'/image.png')", type);
        for (int id : new int[]{30,31}) jdbc.update("insert into storyboard(id,tenant_id,project_id,prompt_document_json,props,video_prompt,current_video_url) values(?,1,2,?,'香烟、手机','抽烟剧情','/video.mp4')", id, document(type).toString());
        jdbc.update("insert into storyboard_asset_reference(id,tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id) values(40,1,2,30,?,10,20)", type);
        jdbc.update("insert into storyboard_asset_reference(id,tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id) values(41,1,2,30,'PROP',11,21)");
        // Same numeric asset IDs outside the project/tenant must never be touched.
        jdbc.update("insert into storyboard(id,tenant_id,project_id,prompt_document_json) values(32,1,3,?),(33,9,2,?)", document(type).toString(), document(type).toString());
        jdbc.update("insert into storyboard_asset_reference(id,tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id) values(42,1,3,32,?,10,20),(43,9,2,33,?,10,20)", type, type);
    }

    @ParameterizedTest
    @CsvSource({"PROP,prop_asset", "CHARACTER,character_asset", "SCENE,scene_asset"})
    void removesReferencesAndKeepsTextAndExistingMedia(String type, String table) throws Exception {
        seed(type, table);
        var impact = service.preview(1,2,type,10);
        assertThat(impact.affectedStoryboardCount()).isEqualTo(2); // union: formal reference + prompt-only
        assertThat(impact.promptMentionCount()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where deleted_at is not null", Integer.class)).isZero();
        service.delete(1,2,type,10);
        for (int id : new int[]{30,31}) {
            var result = json.readTree(jdbc.queryForObject("select prompt_document_json from storyboard where id=?", String.class,id));
            assertThat(result.path("nodes").get(1)).isEqualTo(json.readTree("{\"type\":\"text\",\"text\":\"香烟\"}"));
            assertThat(result.path("nodes").get(2).path("text").asText()).isEqualTo("另一处香烟");
            assertThat(result.path("nodes").get(3)).isEqualTo(document(type).path("nodes").get(3));
            assertThat(result.path("nodes").get(4)).isEqualTo(document(type).path("nodes").get(4));
            assertThat(jdbc.queryForMap("select props,video_prompt,current_video_url from storyboard where id=?",id))
                .containsEntry("props","香烟、手机").containsEntry("video_prompt","抽烟剧情").containsEntry("current_video_url","/video.mp4");
        }
        assertThat(jdbc.queryForObject("select count(*) from storyboard_asset_reference where retired_at is not null",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where deleted_at is not null",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_image_url from asset_visual_variant where id=20",String.class)).isEqualTo("/image.png");
        for (int id : new int[]{32,33}) assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=?",String.class,id)).isEqualTo(document(type).toString());
    }

    @Test
    void rollsBackReferenceAndDocumentChangesIfAssetDeleteFails() throws Exception {
        seed("PROP","prop_asset");
        jdbc.execute("alter table prop_asset add constraint prevent_delete check(deleted_at is null)");
        assertThatThrownBy(() -> service.delete(1,2,"PROP",10)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=30",String.class)).isEqualTo(document("PROP").toString());
        assertThat(jdbc.queryForObject("select count(*) from storyboard_asset_reference where retired_at is not null",Integer.class)).isZero();
    }

    @Test
    void rejectsWrongScopeAndMissingAssets() throws Exception {
        seed("PROP","prop_asset");
        assertThatThrownBy(() -> service.preview(9,2,"PROP",10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(1,3,"PROP",10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(1,2,"PROP",999)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from prop_asset where deleted_at is not null",Integer.class)).isZero();
    }

    @Test
    void convertsStaleEditorMentionsWithoutModifyingTheInput() throws Exception {
        seed("PROP","prop_asset");
        var stale = document("PROP");
        service.delete(1,2,"PROP",10);
        var sanitized = service.retainDeletedAssetText(1,2,stale);
        assertThat(sanitized.path("nodes").get(1).path("type").asText()).isEqualTo("text");
        assertThat(sanitized.path("nodes").get(2).path("text").asText()).isEqualTo("另一处香烟");
        assertThat(stale).isEqualTo(document("PROP"));
        assertThat(service.retainDeletedAssetText(1,3,stale)).isEqualTo(stale);
    }

    @Test
    void unreferencedAssetHasZeroImpactAndCanBeDeleted() {
        jdbc.update("insert into prop_asset(id,tenant_id,project_id,name) values(10,1,2,'香烟')");
        assertThat(service.preview(1,2,"PROP",10).affectedStoryboardCount()).isZero();
        service.delete(1,2,"PROP",10);
        assertThatThrownBy(() -> service.preview(1,2,"PROP",10)).isInstanceOf(BusinessException.class);
    }
}
