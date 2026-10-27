package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class CharacterVariantResolutionServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final AssetNormalizationGuard normalization = mock(AssetNormalizationGuard.class);
    private JdbcTemplate jdbc;
    private CharacterVariantResolutionService service;

    @BeforeEach void setup() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:variant_resolution_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        jdbc.execute("create table script(id bigint primary key,tenant_id bigint,project_id bigint,current_version_id bigint,deleted_at timestamp)");
        jdbc.execute("create table character_asset(id bigint primary key,tenant_id bigint,project_id bigint,script_id bigint,name varchar(100),prompt text,merge_target_id bigint,deleted_at timestamp)");
        jdbc.execute("create table asset_visual_variant(id bigint primary key,tenant_id bigint,project_id bigint,asset_type varchar(32),asset_id bigint,name varchar(100),appearance text,prompt text,is_primary boolean,deleted_at timestamp,updated_at timestamp,generation_status varchar(32),current_image_url varchar(1000),current_image_result_id bigint)");
        jdbc.execute("create table script_episode(id bigint primary key,tenant_id bigint,project_id bigint,script_id bigint,episode_no int,status varchar(32),retired_at timestamp)");
        String schema = new String(new ClassPathResource("db/migration/V63__normalized_assets_visual_variants_and_stable_episodes.sql").getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int start = schema.indexOf("create table asset_visual_variant_episode (");
        jdbc.execute(schema.substring(start, schema.indexOf(';',start)));
        jdbc.execute("create table storyboard(id bigint primary key,tenant_id bigint,project_id bigint,prompt_document_json text,deleted_at timestamp,updated_at timestamp)");
        jdbc.execute("create table storyboard_asset_reference(id bigint primary key,tenant_id bigint,project_id bigint,storyboard_id bigint,asset_type varchar(32),asset_id bigint,variant_id bigint,resolution_status varchar(32),retired_at timestamp,updated_at timestamp)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V154__character_prompt_similarity.sql"),
            new ClassPathResource("db/migration/V152__storyboard_character_voice_overrides.sql"),
            new ClassPathResource("db/migration/V165__character_variant_similarity_resolution.sql")).execute(data);
        jdbc.update("insert into script values(4,1,3,9,null)");
        jdbc.update("insert into character_asset values(11,1,3,4,'马强','原主体设定',null,null)");
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name,appearance,prompt,is_primary,generation_status,current_image_url) values(100,1,3,'CHARACTER',11,'默认形态','','',true,'COMPLETED','/default.png'),(101,1,3,'CHARACTER',11,'墨蓝色工装外套','墨蓝外套配黑工装裤','保留提示词',false,'COMPLETED','/keep.png'),(102,1,3,'CHARACTER',11,'墨蓝工装外套','墨蓝外套配黑工装裤','重复提示词',false,'NOT_STARTED',null)");
        jdbc.update("insert into script_episode values(201,1,3,4,18,'ACTIVE',null),(202,1,3,4,20,'ACTIVE',null),(203,1,3,4,22,'ACTIVE',null)");
        binding(301,101,201,false); binding(302,102,201,true); binding(303,102,202,true); binding(304,101,203,true);
        jdbc.update("insert into storyboard values(401,1,3,?,null,current_timestamp)", "{\"nodes\":[{\"type\":\"mention\",\"mediaType\":\"IMAGE\",\"sourceType\":\"ASSET_VISUAL_VARIANT\",\"sourceId\":102,\"assetId\":11,\"assetType\":\"CHARACTER\",\"imageUrl\":\"/stale.png\"}]}");
        jdbc.update("insert into storyboard_asset_reference values(501,1,3,401,'CHARACTER',11,102,'ASSET_PENDING',null,current_timestamp)");
        jdbc.update("insert into storyboard_character_voice(tenant_id,project_id,storyboard_id,character_id,variant_id,source_project_id,resource_type,resource_id,updated_by,created_at,updated_at) values(1,3,401,11,101,3,'VOICE',600,2,now(),now()),(1,3,401,11,102,3,'VOICE',601,2,now(),now())");
        var character = json.createArrayNode();
        character.addObject().put("id",11).put("name","马强").put("prompt","原主体设定");
        CharacterVariantSimilarityAnalysis.capture(jdbc,json,1,3,character);
        jdbc.update("insert into character_prompt_similarity_check(id,tenant_id,project_id,script_id,script_version_id,execution_id,input_fingerprint,input_json,result_json,created_by,created_at) values(1,1,3,4,9,900,?,?,?,2,now())",
            CharacterPromptSimilarityService.fingerprint(character), character.toString(), "{\"variantPairs\":[{\"characterId\":11,\"leftVariantId\":101,\"rightVariantId\":102,\"score\":99}]}");
        when(permissions.require(eq(1L),eq(3L),anyString())).thenReturn(new TenantContext(2L,1L,20L,"MEMBER"));
        service = new CharacterVariantResolutionService(jdbc,json,permissions,normalization,new DataSourceTransactionManager(data));
    }

    private void binding(long id,long variant,long episode,boolean preferred) {
        jdbc.update("insert into asset_visual_variant_episode(id,tenant_id,project_id,script_id,episode_id,asset_type,asset_id,variant_id,is_preferred,binding_status,created_by,created_at,updated_at) values(?,1,3,4,?,'CHARACTER',11,?,?,'ACTIVE',2,now(),now())",id,episode,variant,preferred);
    }
    private ObjectNode preview() { return service.preview(1,3,1,11,101,102); }
    private CharacterVariantResolutionService.Resolution merge(String fingerprint) {
        return new CharacterVariantResolutionService.Resolution(101,102,"MERGED",101L,fingerprint);
    }

    @Test void previewShowsEpisodeUnionAndImpactWithoutMutatingAssets() {
        var preview=preview();
        assertThat(preview.path("mergedEpisodeNumbers").toString()).isEqualTo("[18,20,22]");
        assertThat(preview.path("affectedStoryboardCount").asInt()).isEqualTo(1);
        assertThat(preview.path("voiceConflictCount").asInt()).isEqualTo(1);
        assertThat(preview.path("recommendedKeptVariantId").asLong()).isEqualTo(101);
        assertThat(preview.path("left").path("currentImageUrl").asText()).isEqualTo("/keep.png");
        assertThat(jdbc.queryForObject("select count(*) from asset_visual_variant where deleted_at is null",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from character_variant_similarity_resolution",Integer.class)).isZero();
    }

    @Test void mergesEpisodeUnionAndEditableReferencesKeepingChosenMediaAndVoiceAndSupportingIdempotence() throws Exception {
        var command=merge(preview().path("fingerprint").asText());
        service.resolve(1,3,1,11,command);
        service.resolve(1,3,1,11,command);
        assertThat(jdbc.queryForObject("select deleted_at from asset_visual_variant where id=102",Object.class)).isNotNull();
        assertThat(jdbc.queryForObject("select current_image_url from asset_visual_variant where id=101",String.class)).isEqualTo("/keep.png");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id=101",String.class)).isEqualTo("保留提示词");
        assertThat(jdbc.queryForList("select episode_id from asset_visual_variant_episode where variant_id=101 and retired_at is null order by episode_id",Long.class)).containsExactly(201L,202L,203L);
        assertThat(jdbc.queryForObject("select is_preferred from asset_visual_variant_episode where variant_id=101 and episode_id=201 and retired_at is null",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select variant_id from storyboard_asset_reference where id=501",Long.class)).isEqualTo(101);
        var mention=json.readTree(jdbc.queryForObject("select prompt_document_json from storyboard where id=401",String.class)).path("nodes").get(0);
        assertThat(mention.path("sourceId").asLong()).isEqualTo(101);
        assertThat(mention.has("imageUrl")).isFalse();
        assertThat(jdbc.queryForObject("select resource_id from storyboard_character_voice where storyboard_id=401 and variant_id=101",Long.class)).isEqualTo(600);
        assertThat(jdbc.queryForObject("select count(*) from storyboard_character_voice where variant_id=102",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from character_variant_similarity_resolution",Integer.class)).isEqualTo(1);
        var stale=json.createObjectNode().putArray("nodes");
        stale.addObject().put("type","mention").put("mediaType","IMAGE").put("sourceType","ASSET_VISUAL_VARIANT").put("sourceId",102).put("assetId",11).put("assetType","CHARACTER");
        var document=json.createObjectNode().set("nodes",stale);
        assertThat(CharacterVariantReferenceFallback.normalizeDocument(jdbc,1,3,document).path("nodes").get(0).path("sourceId").asLong()).isEqualTo(101);
    }

    @Test void keepsDistinctDecisionWithoutChangingAnyBindingAndDecoratesRepeatedReports() throws Exception {
        var before=jdbc.queryForList("select * from asset_visual_variant_episode order by id");
        service.resolve(1,3,1,11,new CharacterVariantResolutionService.Resolution(101,102,"DISTINCT",null,preview().path("fingerprint").asText()));
        assertThat(jdbc.queryForList("select * from asset_visual_variant_episode order by id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select deleted_at from asset_visual_variant where id=102",Object.class)).isNull();
        var row=jdbc.queryForMap("select input_json,result_json from character_prompt_similarity_check where id=1");
        var input=json.readTree(row.get("input_json").toString());
        var report=json.readTree(row.get("result_json").toString());
        CharacterVariantResolutionService.decorate(jdbc,1,3,99,input,input,report,true);
        assertThat(report.path("variantPairs").get(0).path("resolution").asText()).isEqualTo("DISTINCT");
        assertThat(report.path("variantPairs").get(0).path("canResolve").asBoolean()).isFalse();
        assertThat(report.path("variantPairs").get(0).path("canInspect").asBoolean()).isTrue();
    }

    @Test void keepsTheDefaultVariantAndRejectsMergingItIntoAnotherOutfit() throws Exception {
        jdbc.update("update asset_visual_variant set is_primary=false where id=100");
        jdbc.update("update asset_visual_variant set is_primary=true where id=101");
        freezeCurrent();
        var preview=preview();
        assertThat(preview.path("recommendedKeptVariantId").asLong()).isEqualTo(101);
        assertThatThrownBy(()->service.resolve(1,3,1,11,new CharacterVariantResolutionService.Resolution(101,102,"MERGED",102L,preview.path("fingerprint").asText())))
            .hasMessageContaining("默认");
        service.resolve(1,3,1,11,merge(preview.path("fingerprint").asText()));
        assertThat(jdbc.queryForObject("select is_primary from asset_visual_variant where id=101",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select prompt from character_asset where id=11",String.class)).isEqualTo("原主体设定");
    }

    @Test void ignoresHistoricalStoryboardsAndRequiresEditPermission() {
        jdbc.update("insert into storyboard values(402,1,3,'historical 102',current_timestamp,current_timestamp)");
        jdbc.update("insert into storyboard_asset_reference values(502,1,3,402,'CHARACTER',11,102,'ASSET_PENDING',null,current_timestamp)");
        var command=merge(preview().path("fingerprint").asText());
        service.resolve(1,3,1,11,command);
        assertThat(jdbc.queryForObject("select variant_id from storyboard_asset_reference where id=502",Long.class)).isEqualTo(102);
        assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=402",String.class)).isEqualTo("historical 102");
        doThrow(new com.antshorttv.common.BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN,"无编辑权限"))
            .when(permissions).require(1L,3L,"ELEMENT:EDIT");
        assertThatThrownBy(()->preview()).hasMessage("无编辑权限");
        assertThatThrownBy(()->service.resolve(1,3,1,11,command)).hasMessage("无编辑权限");
    }

    @Test void unrelatedPairsRemainActionableAfterOneMergeAndChangedDistinctDecisionsAreReconsidered() throws Exception {
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name,appearance,prompt,is_primary,generation_status) values(103,1,3,'CHARACTER',11,'白衬衫','白衬衫','白色衬衫',false,'NOT_STARTED')");
        freezeCurrent();
        jdbc.update("update character_prompt_similarity_check set result_json=? where id=1", "{\"variantPairs\":[{\"characterId\":11,\"leftVariantId\":101,\"rightVariantId\":102},{\"characterId\":11,\"leftVariantId\":100,\"rightVariantId\":103}]}");
        var frozen=json.readTree(jdbc.queryForObject("select input_json from character_prompt_similarity_check where id=1",String.class));
        service.resolve(1,3,1,11,merge(preview().path("fingerprint").asText()));
        var current=json.createArrayNode(); current.addObject().put("id",11).put("name","马强").put("prompt","原主体设定");
        CharacterVariantSimilarityAnalysis.capture(jdbc,json,1,3,current);
        var report=json.readTree(jdbc.queryForObject("select result_json from character_prompt_similarity_check where id=1",String.class));
        CharacterVariantResolutionService.decorate(jdbc,1,3,1,frozen,current,report,true);
        assertThat(report.path("variantPairs").get(0).path("resolution").asText()).isEqualTo("MERGED");
        assertThat(report.path("variantPairs").get(1).path("canResolve").asBoolean()).isTrue();
        var different=service.preview(1,3,1,11,100,103);
        service.resolve(1,3,1,11,new CharacterVariantResolutionService.Resolution(100,103,"DISTINCT",null,different.path("fingerprint").asText()));
        jdbc.update("update asset_visual_variant set appearance='白衬衫，袖口破损' where id=103");
        var revised=json.createArrayNode(); revised.addObject().put("id",11).put("name","马强").put("prompt","原主体设定");
        CharacterVariantSimilarityAnalysis.capture(jdbc,json,1,3,revised);
        var next=json.readTree("{\"variantPairs\":[{\"characterId\":11,\"leftVariantId\":100,\"rightVariantId\":103}]}");
        CharacterVariantResolutionService.decorate(jdbc,1,3,2,revised,revised,next,true);
        assertThat(next.path("variantPairs").get(0).has("resolution")).isFalse();
        assertThat(next.path("variantPairs").get(0).path("canResolve").asBoolean()).isTrue();
    }

    private void freezeCurrent() {
        var current=json.createArrayNode(); current.addObject().put("id",11).put("name","马强").put("prompt","原主体设定");
        CharacterVariantSimilarityAnalysis.capture(jdbc,json,1,3,current);
        jdbc.update("update character_prompt_similarity_check set input_json=?,input_fingerprint=? where id=1",current.toString(),CharacterPromptSimilarityService.fingerprint(current));
    }

    @Test void movesUnmatchedVoiceOverridesToTheRetainedVariant() {
        jdbc.update("delete from storyboard_character_voice where variant_id=101");
        var command=merge(preview().path("fingerprint").asText());
        service.resolve(1,3,1,11,command);
        assertThat(jdbc.queryForObject("select resource_id from storyboard_character_voice where storyboard_id=401 and variant_id=101",Long.class)).isEqualTo(601);
    }

    @Test void blocksAChangedDescriptionBeforeAnyDecisionIsWritten() {
        jdbc.update("update asset_visual_variant set appearance='墨蓝外套，衣袖破损' where id=102");
        assertThatThrownBy(()->preview()).hasMessageContaining("变化");
        assertThat(jdbc.queryForObject("select count(*) from character_variant_similarity_resolution",Integer.class)).isZero();
    }

    @Test void rejectsChangedImagesBeforeConfirmationAndLeavesEverythingUntouched() {
        var command=merge(preview().path("fingerprint").asText());
        jdbc.update("update asset_visual_variant set current_image_url='/new.png' where id=101");
        assertThatThrownBy(()->service.resolve(1,3,1,11,command)).hasMessageContaining("变化");
        assertThat(jdbc.queryForObject("select deleted_at from asset_visual_variant where id=102",Object.class)).isNull();
        assertThat(jdbc.queryForObject("select variant_id from storyboard_asset_reference where id=501",Long.class)).isEqualTo(102);
    }

    @Test void rejectsUnreportedPairsCrossTenantStaleVersionsAndGenerationInProgress() {
        assertThatThrownBy(()->service.preview(1,3,1,11,100,102)).hasMessageContaining("检查");
        assertThatThrownBy(()->service.preview(2,3,1,11,101,102)).isInstanceOf(RuntimeException.class);
        jdbc.update("update asset_visual_variant set generation_status='GENERATING' where id=102");
        var preview=preview();
        assertThat(preview.path("canMerge").asBoolean()).isFalse();
        assertThatThrownBy(()->service.resolve(1,3,1,11,merge(preview.path("fingerprint").asText()))).hasMessageContaining("生成");
        jdbc.update("update script set current_version_id=10 where id=4");
        assertThatThrownBy(()->preview()).hasMessageContaining("版本");
    }

    @Test void rollbackRestoresBindingsIfMalformedStoryboardPreventsReferenceMigration() {
        jdbc.update("update storyboard set prompt_document_json='invalid 102' where id=401");
        var command=merge(preview().path("fingerprint").asText());
        assertThatThrownBy(()->service.resolve(1,3,1,11,command)).hasMessageContaining("格式");
        assertThat(jdbc.queryForObject("select variant_id from asset_visual_variant_episode where id=303",Long.class)).isEqualTo(102);
        assertThat(jdbc.queryForObject("select variant_id from storyboard_asset_reference where id=501",Long.class)).isEqualTo(102);
        assertThat(jdbc.queryForObject("select deleted_at from asset_visual_variant where id=102",Object.class)).isNull();
    }
}
