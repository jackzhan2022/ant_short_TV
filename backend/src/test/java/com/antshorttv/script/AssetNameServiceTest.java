package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetNameServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AssetNameService names;
    @Autowired AssetVisualVariantService variants;
    private static final long TENANT=99801, PROJECT=99802, ASSET=99803;

    @BeforeEach void seed() {
        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,role_type,status,prompt,appearance,source,normalized_name,created_by,created_at,updated_at) values (99803,99801,99802,'原名','LEAD','CONFIRMED','原提示词','原外貌','AI','固定身份',1,now(),now())");
        jdbc.update("insert into scene_asset (id,tenant_id,project_id,name,scene_type,status,prompt,created_by,created_at,updated_at) values (99803,99801,99802,'原名','INDOOR','CONFIRMED','原提示词',1,now(),now())");
        jdbc.update("insert into prop_asset (id,tenant_id,project_id,name,prop_type,status,prompt,created_by,created_at,updated_at) values (99803,99801,99802,'原名','KEY_PROP','CONFIRMED','原提示词',1,now(),now())");
    }

    private void renameAsset(long tenant,long project,String type,long id,String name) {
        names.renameAsset(tenant,project,type,id,name);
    }
    private void renameVariant(long tenant,long project,long id,String name) {
        names.renameVariant(tenant,project,id,name);
    }
    private Map<String,Object> snapshot(String table,long id) {
        var row=jdbc.queryForMap("select * from "+table+" where id=?",id);
        row.remove("name");row.remove("updated_at");return row;
    }
    private long variant(String type) {
        return variants.create(TENANT,PROJECT,type,ASSET,1L,new AssetVisualVariantService.VariantCommand(
            "原形态","外貌","提示词","MANUAL","COMPLETED",null,"/kept.png",true,"provider-identity")).id();
    }
    @ParameterizedTest @ValueSource(strings={"CHARACTER","SCENE","PROP"})
    void renamesOnlyNameForEachAssetType(String type) {
        String table=type.toLowerCase()+"_asset";
        var before=snapshot(table,ASSET);
        renameAsset(TENANT,PROJECT,type,ASSET,"  新名称  ");
        assertThat(jdbc.queryForObject("select name from "+table+" where id=99803",String.class)).isEqualTo("新名称");
        assertThat(snapshot(table,ASSET)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"CHARACTER","SCENE","PROP"})
    void renamesPrimaryVariantWithoutChangingMediaPromptOrIdentity(String type) {
        long id=variant(type);var before=snapshot("asset_visual_variant",id);
        renameVariant(TENANT,PROJECT,id,"  新形态  ");
        assertThat(jdbc.queryForObject("select name from asset_visual_variant where id=?",String.class,id)).isEqualTo("新形态");
        assertThat(snapshot("asset_visual_variant",id)).isEqualTo(before);
    }
    @Test void validatesTrimmedNameAndMaximumLength() {
        long id=variant("CHARACTER");
        for(String bad:new String[]{null,""," \t ","名".repeat(101)}) {
            assertThatThrownBy(()->renameAsset(TENANT,PROJECT,"CHARACTER",ASSET,bad)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->renameVariant(TENANT,PROJECT,id,bad)).isInstanceOf(BusinessException.class);
        }
        renameAsset(TENANT,PROJECT,"CHARACTER",ASSET," "+"名".repeat(100)+" ");
        renameVariant(TENANT,PROJECT,id,"形".repeat(100));
    }
    @Test void rejectsMissingDeletedAndWrongScope() {
        long id=variant("CHARACTER");
        for(long[] scope:new long[][]{{TENANT+1,PROJECT,ASSET},{TENANT,PROJECT+1,ASSET},{TENANT,PROJECT,ASSET+1}})
            assertThatThrownBy(()->renameAsset(scope[0],scope[1],"CHARACTER",scope[2],"新名")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->renameAsset(TENANT,PROJECT,"ALL",ASSET,"新名")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->renameVariant(TENANT+1,PROJECT,id,"新名")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->renameVariant(TENANT,PROJECT+1,id,"新名")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->renameVariant(TENANT,PROJECT,id+999,"新名")).isInstanceOf(BusinessException.class);
        jdbc.update("update character_asset set deleted_at=now() where id=?",ASSET);
        assertThatThrownBy(()->renameAsset(TENANT,PROJECT,"CHARACTER",ASSET,"新名")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->renameVariant(TENANT,PROJECT,id,"新名")).isInstanceOf(BusinessException.class);
        jdbc.update("update character_asset set deleted_at=null where id=?",ASSET);
        jdbc.update("update asset_visual_variant set deleted_at=now() where id=?",id);
        assertThatThrownBy(()->renameVariant(TENANT,PROJECT,id,"新名")).isInstanceOf(BusinessException.class);
    }
    @Test void preservesLegacySeparatorAndSlashCharactersInsideNames() throws Exception {
        long id=variant("CHARACTER");
        String document="{\"version\":2,\"nodes\":[{\"type\":\"mention\",\"mediaType\":\"IMAGE\",\"sourceType\":\"ASSET_VISUAL_VARIANT\",\"sourceId\":"+id+",\"displayName\":\"原名·原形态\"}]}";
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,prompt_document_json,status,created_by,created_at,updated_at) values (99820,99801,99802,1,1,'正文',?,'DRAFT',1,now(),now())",document);
        renameAsset(TENANT,PROJECT,"CHARACTER",ASSET,"A / B");
        String result=jdbc.queryForObject("select prompt_document_json from storyboard where id=99820",String.class);
        assertThat(new ObjectMapper().readTree(result).path("nodes").get(0).path("displayName").asText()).isEqualTo("A / B·原形态");
    }
    @Test void updatesOnlyMatchingActiveMentionLabels() throws Exception {
        long id=variant("CHARACTER");
        String document="""
            {"version":2,"nodes":[
              {"type":"text","text":"原名和原形态保持原文"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":%d,"assetType":"CHARACTER","assetId":99803,"variantId":%d,"displayName":"原名 / 原形态","imageUrl":"/unchanged.png"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":%d,"displayName":"原名 / 原形态"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":%d,"assetType":"SCENE","assetId":99803,"displayName":"其他资产"},
              {"type":"mention","mediaType":"VIDEO","sourceType":"AI_VIDEO_RESULT","sourceId":%d,"displayName":"普通视频"}
            ]}
            """.formatted(id,id,id,id,id);
        for(long shot=99810;shot<=99812;shot++) {
            jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,prompt_document_json,status,created_by,created_at,updated_at) values (?,99801,99802,1,1,'原名原形态',?,'DRAFT',1,now(),now())",shot,document);
        }
        jdbc.update("update storyboard set deleted_at=now() where id=99811");
        jdbc.update("update storyboard set project_id=99899 where id=99812");
        renameAsset(TENANT,PROJECT,"CHARACTER",ASSET,"新资产");
        renameVariant(TENANT,PROJECT,id,"新形态");
        var tree=new ObjectMapper().readTree(jdbc.queryForObject("select prompt_document_json from storyboard where id=99810",String.class));
        assertThat(tree.path("nodes").get(0).path("text").asText()).isEqualTo("原名和原形态保持原文");
        for(int n=1;n<=2;n++) assertThat(tree.path("nodes").get(n).path("displayName").asText()).isEqualTo("新资产 / 新形态");
        assertThat(tree.path("nodes").get(1).path("imageUrl").asText()).isEqualTo("/unchanged.png");
        assertThat(tree.path("nodes").get(3).path("displayName").asText()).isEqualTo("其他资产");
        assertThat(tree.path("nodes").get(4).path("displayName").asText()).isEqualTo("普通视频");
        for(long shot=99811;shot<=99812;shot++) assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=?",String.class,shot)).isEqualTo(document);
        assertThat(jdbc.queryForObject("select visual_description from storyboard where id=99810",String.class)).isEqualTo("原名原形态");
    }
}
