package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.ai.*;
import com.antshorttv.execution.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class CharacterPromptSimilarityServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final ScriptAiOperationService operations = mock(ScriptAiOperationService.class);
    private final AssetNormalizationGuard normalization = mock(AssetNormalizationGuard.class);
    private final AiExecutionService executions = mock(AiExecutionService.class);
    private final AiExecutionResponseMapper responses = mock(AiExecutionResponseMapper.class);
    private final AiInvocationService invocation = mock(AiInvocationService.class);
    private JdbcTemplate jdbc;
    private CharacterPromptSimilarityService service;
    private AiExecutionTaskEntity task;

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:similarity_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        jdbc.execute("create table script (id bigint primary key,tenant_id bigint,project_id bigint,current_version_id bigint,deleted_at timestamp)");
        jdbc.execute("create table character_asset (id bigint primary key,tenant_id bigint,project_id bigint,script_id bigint,name varchar(100),prompt text,source varchar(32),updated_at timestamp,merge_target_id bigint,deleted_at timestamp)");
        jdbc.execute("create table asset_visual_variant (id bigint primary key,tenant_id bigint,project_id bigint,asset_type varchar(32),asset_id bigint,name varchar(100),appearance text,prompt text,is_primary boolean,deleted_at timestamp)");
        jdbc.execute("create table ai_execution_task (id bigint primary key,tenant_id bigint,status varchar(32),error_message text)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V154__character_prompt_similarity.sql"),
            new ClassPathResource("db/migration/V165__character_variant_similarity_resolution.sql")).execute(data);
        jdbc.update("insert into script(id,tenant_id,project_id,current_version_id) values(4,1,3,9)");
        jdbc.update("insert into character_asset(id,tenant_id,project_id,script_id,name,prompt) values(11,1,3,4,'角色甲','脸型：长窄脸，细直眉'),(12,1,3,4,'角色乙','脸型：窄长脸，平直细眉')");
        jdbc.update("insert into ai_execution_task values(900,1,'PENDING',null)");
        when(permissions.require(eq(1L), eq(3L), anyString())).thenReturn(new TenantContext(2L,1L,20L,"MEMBER"));
        var execution = mock(AiExecutionResponse.class);
        when(execution.id()).thenReturn(900L);
        when(operations.submit(any(),eq(3L),eq(AiBusinessScene.PROMPT_GENERATE),eq(CharacterPromptSimilarityService.OPERATION),
            eq(4L),eq(9L),any(),anyString(),anyString())).thenReturn(execution);
        task = new AiExecutionTaskEntity();
        task.id=900L; task.tenantId=1L; task.userId=2L; task.projectId=3L; task.requestedModelId=5L;
        task.executionVersion=1; task.claimToken="claim"; task.status="RUNNING";
        task.claimExpiresAt=LocalDateTime.now().plusMinutes(5);
        when(executions.requireTaskForUpdate(900L)).thenReturn(task);
        service = new CharacterPromptSimilarityService(jdbc,json,permissions,operations,normalization,executions,
            responses,invocation,new DataSourceTransactionManager(data));
    }

    private ScriptAiOperationEntity operation() {
        var op = new ScriptAiOperationEntity();
        op.id=80L; op.tenantId=1L; op.projectId=3L; op.scriptId=4L; op.scriptVersionId=9L;
        return op;
    }
    private AiExecutionContext context() { return new AiExecutionContext(task, new AiExecutionClaim(900L,901L,"claim",1,"SUBMIT")); }
    private String result() {
        return """
            {"summary":"两人面部设定接近","pairs":[{"leftId":11,"rightId":12,"score":88,
            "sharedFeatures":["长窄脸、细直眉"],"differences":[],"suggestions":["调整其中一人的下颌与眉眼结构"]}],
            "insufficient":[],"hasMore":false,"variantPairs":[],"variantsHasMore":false,"revisions":[]}
            """;
    }
    @SuppressWarnings("unchecked")
    private void modelReturns(String content) {
        AiInvocationResult<AiTextResponse> call = mock(AiInvocationResult.class);
        when(call.content()).thenReturn(content);
        when(call.response()).thenReturn(new AiTextResponse(content,"request",10,20,30,1L,Map.of()));
        when(invocation.invokeText(any())).thenReturn(call);
    }

    @Test void checksDuplicateCostumesForASingleCharacterEvenWithoutImagesOrBodyPrompt() {
        jdbc.update("delete from character_asset where id=12");
        jdbc.update("update character_asset set prompt='' where id=11");
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name) values(101,1,3,'CHARACTER',11,'墨蓝色工装外套'),(102,1,3,'CHARACTER',11,'墨蓝工装外套')");
        service.submit(1,3,"variants");
        modelReturns("{\"summary\":\"检查完成\",\"pairs\":[],\"insufficient\":[],\"hasMore\":false,\"revisions\":[],\"variantPairs\":[],\"variantsHasMore\":false}");
        service.analyze(operation(),context());
        var report=service.status(1,3);
        assertThat(report.path("characters").get(0).path("variants")).hasSize(2);
        assertThat(report.path("analysis").path("variantPairs")).hasSize(1);
        assertThat(report.path("analysis").path("variantPairs").get(0).path("characterId").asLong()).isEqualTo(11L);
        assertThat(report.path("stale").asBoolean()).isFalse();
        jdbc.update("update asset_visual_variant set name='炭灰工装外套' where id=102");
        assertThat(service.status(1,3).path("stale").asBoolean()).isTrue();
    }

    @Test void neverTreatsMatchingClothesAcrossCharactersAsDuplicateVariants() {
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name) values(101,1,3,'CHARACTER',11,'墨蓝色工装外套'),(102,1,3,'CHARACTER',12,'墨蓝工装外套'),(103,2,3,'CHARACTER',11,'不应泄露'),(104,1,30,'CHARACTER',11,'不应泄露'),(105,1,3,'PROP',11,'不应泄露')");
        service.submit(1,3,"variants");
        var input=service.status(1,3).path("characters");
        assertThat(input.get(0).path("variants")).hasSize(1);
        assertThat(input.get(1).path("variants")).hasSize(1);
        modelReturns(result());
        service.analyze(operation(),context());
        assertThat(service.status(1,3).path("analysis").path("variantPairs")).isEmpty();
    }

    @Test void includesPrimaryBodyPromptAndIgnoresDeletedVariants() {
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name,is_primary,deleted_at) values(101,1,3,'CHARACTER',11,'默认形态',true,null),(102,1,3,'CHARACTER',11,'旧形态',false,current_timestamp)");
        service.submit(1,3,"variants");
        var variants=service.status(1,3).path("characters").get(0).path("variants");
        assertThat(variants).hasSize(1);
        assertThat(variants.get(0).path("prompt").asText()).isEqualTo("脸型：长窄脸，细直眉");
        assertThat(variants.get(0).path("isPrimary").asBoolean()).isTrue();
        jdbc.update("update asset_visual_variant set deleted_at=current_timestamp where id=101");
        assertThat(service.status(1,3).path("stale").asBoolean()).isTrue();
    }

    @Test void rejectsModelVariantPairsWhoseVariantsBelongToDifferentCharacters() {
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name) values(101,1,3,'CHARACTER',11,'墨蓝色工装外套'),(102,1,3,'CHARACTER',12,'墨蓝工装外套')");
        service.submit(1,3,"variants");
        modelReturns(result().replace("\"variantPairs\":[]", "\"variantPairs\":[{\"characterId\":11,\"leftVariantId\":101,\"rightVariantId\":102,\"score\":99,\"sharedFeatures\":[\"同套衣服\"],\"differences\":[],\"suggestions\":[\"核实重复\"]}]"));
        assertThatThrownBy(() -> service.analyze(operation(),context())).isInstanceOf(CharacterPromptSimilarityAnalysis.InvalidResult.class);
        assertThat(jdbc.queryForObject("select result_json from character_prompt_similarity_check",String.class)).isNull();
    }

    @Test void freezesOnlyCurrentProjectCanonicalCharactersAndDetectsLaterEdits() {
        jdbc.update("insert into character_asset(id,tenant_id,project_id,script_id,name,prompt) values(13,2,3,4,'其他租户','private'),(14,1,30,4,'其他项目','private'),(15,1,3,40,'旧剧本','private')");
        service.submit(1,3,"key");
        var original = service.status(1,3);
        assertThat(original.path("characters")).hasSize(2);
        assertThat(original.path("stale").asBoolean()).isFalse();
        jdbc.update("update character_asset set prompt='宽方脸、浓眉' where id=11");
        var stale = service.status(1,3);
        assertThat(stale.path("stale").asBoolean()).isTrue();
        assertThat(stale.path("characters").get(0).path("prompt").asText()).isEqualTo("脸型：长窄脸，细直眉");
        verifyNoInteractions(invocation);
    }

    @Test void rejectsInsufficientPromptsBeforeReservingOrCallingAModel() {
        jdbc.update("update character_asset set prompt='' where id=12");
        assertThatThrownBy(() -> service.submit(1,3,"key")).hasMessageContaining("至少两个");
        assertThat(jdbc.queryForObject("select count(*) from character_prompt_similarity_check",Integer.class)).isZero();
        verifyNoInteractions(operations,invocation);
    }

    @Test void requiresPermissionBeforeReadingOrSubmitting() {
        doThrow(new com.antshorttv.common.BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN,"denied"))
            .when(permissions).require(eq(1L),eq(3L),anyString());
        assertThatThrownBy(() -> service.status(1,3)).hasMessage("denied");
        assertThatThrownBy(() -> service.submit(1,3,"key")).hasMessage("denied");
        verifyNoInteractions(operations,invocation);
    }

    @Test void savesValidatedAnalysisWithoutChangingAnyPrompt() {
        service.submit(1,3,"key");
        var before = jdbc.queryForList("select id,prompt from character_asset order by id");
        modelReturns(result());
        var outcome = service.analyze(operation(),context());
        assertThat(outcome.resultType()).isEqualTo(CharacterPromptSimilarityService.OPERATION);
        assertThat(outcome.invocations()).hasSize(1);
        assertThat(service.status(1,3).path("analysis").path("pairs").get(0).path("score").asInt()).isEqualTo(88);
        assertThat(jdbc.queryForList("select id,prompt from character_asset order by id")).isEqualTo(before);
        service.analyze(operation(),context());
        verify(invocation,times(1)).invokeText(any());
    }

    @Test void allowsLongRunningChecksAndHonorsTheConfiguredRequestTimeout() {
        service.submit(1,3,"key");
        modelReturns(result());
        var request = org.mockito.ArgumentCaptor.forClass(AiInvocationRequest.class);
        service.analyze(operation(),context());
        verify(invocation).invokeText(request.capture());
        assertThat(request.getValue().textRequest().timeoutSeconds()).isEqualTo(600);
        assertThat(request.getValue().textRequest().retryCount()).isZero();

        jdbc.update("update character_prompt_similarity_check set result_json=null,completed_at=null");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"timeoutSeconds",720);
        service.analyze(operation(),context());
        verify(invocation,times(2)).invokeText(request.capture());
        assertThat(request.getValue().textRequest().timeoutSeconds()).isEqualTo(720);
    }

    @Test void timedOutCheckPreservesCallCorrelationAndDoesNotPublishAResult() {
        service.submit(1,3,"key");
        when(invocation.invokeText(any())).thenThrow(
            new AiGatewayException(com.antshorttv.common.ErrorCode.AI_PROVIDER_TIMEOUT,"timeout",99L));
        assertThatThrownBy(() -> service.analyze(operation(),context()))
            .isInstanceOfSatisfying(CharacterSimilarityTimeoutException.class, failure -> {
                assertThat(failure.getAiCallLogId()).isEqualTo(99L);
                assertThat(failure.getExecutionId()).isEqualTo(900L);
                assertThat(failure.getAttemptId()).isEqualTo(901L);
                assertThat(failure.getMessage()).contains("停止自动重试");
            });
        assertThat(jdbc.queryForObject("select result_json from character_prompt_similarity_check",String.class)).isNull();
        verify(invocation,times(1)).invokeText(any());
    }

    @Test void canceledOrSupersededExecutionCannotPublishAReport() {
        service.submit(1,3,"key");
        modelReturns(result());
        task.claimToken="new-claim";
        assertThatThrownBy(() -> service.analyze(operation(),context())).isInstanceOf(AiExecutionClaimLostException.class);
        assertThat(jdbc.queryForObject("select result_json from character_prompt_similarity_check",String.class)).isNull();
    }

    @Test void missingPromptsAreAlwaysReportedEvenIfTheModelOmitsThem() {
        jdbc.update("insert into character_asset(id,tenant_id,project_id,script_id,name) values(16,1,3,4,'未填写角色')");
        service.submit(1,3,"key");
        modelReturns(result());
        service.analyze(operation(),context());
        var issues = service.status(1,3).path("analysis").path("insufficient");
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).path("characterId").asLong()).isEqualTo(16);
        assertThat(issues.get(0).path("reason").asText()).contains("未参与");
    }

    @Test void activeIdenticalCheckIsReusedInsteadOfReservingTwice() {
        service.submit(1,3,"first");
        when(executions.requireTask(900L)).thenReturn(task);
        service.submit(1,3,"second");
        verify(operations,times(1)).submit(any(),anyLong(),any(),anyString(),anyLong(),anyLong(),any(),anyString(),anyString());
        assertThat(jdbc.queryForObject("select count(*) from character_prompt_similarity_check",Integer.class)).isEqualTo(1);
    }

    private static final String STRUCTURED = "## 任务：角色设定图\r\n角色描述:\r\n- **脸型**: 长窄脸，细直眉\r\n- **性别**: 男\r\n- **年龄**: 32岁\r\n- **上身着装**: 卡其工装衬衫\r\n- **鞋子**: 黑色工作鞋\r\n";
    private static final String NEW_FACE = "轮廓与比例：长方脸，下颌外扩，下巴宽平；眉眼：浓短平眉，窄眼距；鼻部：宽直鼻梁，方圆鼻头；嘴唇：宽嘴薄唇";

    private String generateRevision() throws Exception {
        jdbc.update("update character_asset set prompt=? where id=11", STRUCTURED);
        service.submit(1,3,"revision");
        var output=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(result());
        output.putArray("revisions").addObject().put("characterId",11L).put("face",NEW_FACE)
            .putArray("changes").add("调整下颌、眉眼和鼻部");
        modelReturns(output.toString());
        service.analyze(operation(),context());
        return service.status(1,3).path("analysis").path("revisions").get(0).path("prompt").asText();
    }

    @Test void createsACompleteRevisionWhilePreservingEveryOtherFieldAndLineEnding() throws Exception {
        String prompt=generateRevision();
        assertThat(prompt).isEqualTo(STRUCTURED.replace("长窄脸，细直眉",NEW_FACE));
        assertThat(jdbc.queryForObject("select prompt from character_asset where id=11",String.class)).isEqualTo(STRUCTURED);
        var revision=service.status(1,3).path("analysis").path("revisions").get(0);
        assertThat(revision.path("originalFace").asText()).isEqualTo("长窄脸，细直眉");
        assertThat(revision.path("changes")).hasSize(1);
    }

    @Test void appliesAnExplicitlyReviewedRevisionToOnlyItsCharacterAndIsIdempotent() throws Exception {
        String prompt=generateRevision();
        long checkId=service.status(1,3).path("id").asLong();
        service.apply(1,3,checkId,11,prompt);
        service.apply(1,3,checkId,11,prompt);
        assertThat(jdbc.queryForObject("select prompt from character_asset where id=11",String.class)).isEqualTo(prompt);
        assertThat(jdbc.queryForObject("select source from character_asset where id=11",String.class)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("select prompt from character_asset where id=12",String.class)).isEqualTo("脸型：窄长脸，平直细眉");
        assertThat(service.status(1,3).path("stale").asBoolean()).isTrue();
        verify(invocation,times(1)).invokeText(any());
    }

    @Test void applyingDoesNotOverwriteConcurrentEditsOrApplyToAnUnproposedCharacter() throws Exception {
        String prompt=generateRevision();
        long checkId=service.status(1,3).path("id").asLong();
        jdbc.update("update character_asset set prompt='另一位同事的修改' where id=11");
        assertThatThrownBy(() -> service.apply(1,3,checkId,11,prompt)).hasMessageContaining("避免覆盖");
        assertThatThrownBy(() -> service.apply(1,3,checkId,12,prompt)).hasMessageContaining("没有这个角色的修改稿");
        assertThat(jdbc.queryForObject("select prompt from character_asset where id=11",String.class)).isEqualTo("另一位同事的修改");
    }

    @Test void applyingRejectsChangedScriptVersionsAndLackOfEditPermission() throws Exception {
        String prompt=generateRevision();
        long checkId=service.status(1,3).path("id").asLong();
        jdbc.update("update script set current_version_id=10 where id=4");
        assertThatThrownBy(() -> service.apply(1,3,checkId,11,prompt)).hasMessageContaining("剧本版本已变化");
        doThrow(new com.antshorttv.common.BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN,"no edit permission"))
            .when(permissions).require(1L,3L,"ELEMENT:EDIT");
        assertThatThrownBy(() -> service.apply(1,3,checkId,11,prompt)).hasMessage("no edit permission");
        assertThat(jdbc.queryForObject("select prompt from character_asset where id=11",String.class)).isEqualTo(STRUCTURED);
    }

    @Test void structuredSimilarPairsMustIncludeARealRevisionAndCannotInjectExtraFields() throws Exception {
        var input=json.createArrayNode();
        input.addObject().put("id",11).put("prompt",STRUCTURED);
        input.addObject().put("id",12).put("prompt","- **脸型**: 长脸");
        assertThatThrownBy(() -> CharacterPromptSimilarityAnalysis.validate(json,input,json.readTree(result())))
            .isInstanceOf(CharacterPromptSimilarityAnalysis.InvalidResult.class);
        var output=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(result());
        var revision=output.putArray("revisions").addObject().put("characterId",11).put("face",NEW_FACE);
        revision.putArray("changes").add("区分轮廓");
        CharacterPromptSimilarityAnalysis.validate(json,input,output);
        revision.put("face",NEW_FACE+"\n- **年龄**: 18岁");
        assertThatThrownBy(() -> CharacterPromptSimilarityAnalysis.validate(json,input,output))
            .isInstanceOf(CharacterPromptSimilarityAnalysis.InvalidResult.class);
    }

    @Test void rejectsInventedCharactersDuplicatePairsAndOutOfRangeScores() throws Exception {
        var input=json.readTree("[{\"id\":11,\"prompt\":\"face A\"},{\"id\":12,\"prompt\":\"face B\"}]");
        CharacterPromptSimilarityAnalysis.validate(json,input,json.readTree(result()));
        for(String invalid : List.of(result().replace("\"rightId\":12","\"rightId\":99"),
            result().replace("\"rightId\":12","\"rightId\":11"), result().replace("\"score\":88","\"score\":101"))) {
            assertThatThrownBy(() -> CharacterPromptSimilarityAnalysis.validate(json,input,json.readTree(invalid)))
                .isInstanceOf(CharacterPromptSimilarityAnalysis.InvalidResult.class);
        }
        var duplicate=json.readTree(result());
        ((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("pairs")).add(duplicate.path("pairs").get(0).deepCopy());
        assertThatThrownBy(() -> CharacterPromptSimilarityAnalysis.validate(json,input,duplicate))
            .isInstanceOf(CharacterPromptSimilarityAnalysis.InvalidResult.class);
    }
}
