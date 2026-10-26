package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.script.StoryboardPromptCompiler;
import com.antshorttv.storage.ObjectStorageService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.public-base-url=https://app.example")
@Transactional
class VideoTaskReferenceResolverTest {
    private static final long TENANT_ID = 97001L;
    private static final long PROJECT_ID = 97002L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VideoTaskReferenceResolver resolver;

    @Autowired private AiVideoTaskService videoTasks;
    @Autowired private AiVideoTaskMapper videoTaskMapper;
    @Autowired private AiVideoResultMapper videoResultMapper;
    @Autowired private SeedanceArkVideoProviderAdapter seedance;
    @MockBean private AiVideoCoverService videoCovers;

    @MockBean
    private ObjectStorageService objectStorageService;
    @MockBean private com.antshorttv.rbac.ProjectPermissionGuard projectPermissions;

    @BeforeEach
    void seedReferences() {
        when(objectStorageService.modelAccessUrl(anyString(), any(Duration.class)))
            .thenAnswer(invocation -> "https://cos.example/" + invocation.getArgument(0, String.class));
        jdbc.update("""
            insert into ai_image_result
              (id, tenant_id, project_id, task_id, target_type, target_id, image_url,
               storage_path, mime_type, width, height, file_size, is_selected, status,
               created_at, updated_at)
            values (97101, ?, ?, 1, 'VISUAL_VARIANT', 97111, '/materials/image.png',
                    '/materials/image.png', 'image/png', 720, 1280, 1000, true, 'ACTIVE', now(), now())
            """, TENANT_ID, PROJECT_ID);
        jdbc.update("""
            insert into asset_visual_variant
              (id, tenant_id, project_id, asset_type, asset_id, name, source_type,
               generation_status, current_image_result_id, current_image_url, is_primary,
               created_by, created_at, updated_at)
            values (97111, ?, ?, 'CHARACTER', 97112, '林晚', 'USER', 'SUCCEEDED',
                    97101, '/materials/image.png', false, 1, now(), now())
            """, TENANT_ID, PROJECT_ID);
        insertMaterial(97201L, TENANT_ID, PROJECT_ID, "VIDEO", "参考运镜", "/materials/reference.mp4",
            "video/mp4", 2_000L, 1280, 720, "mp4", new BigDecimal("5.5"), null);
        insertMaterial(97202L, TENANT_ID, PROJECT_ID, "AUDIO", "背景音乐", "/materials/reference.mp3",
            "audio/mpeg", 1_500L, null, null, "mp3", new BigDecimal("5.5"), null);
        insertMaterial(97203L, TENANT_ID + 1, PROJECT_ID, "VIDEO", "跨团队视频", "/materials/cross.mp4",
            "video/mp4", 2_000L, 1280, 720, "mp4", new BigDecimal("4"), null);
        insertMaterial(97204L, TENANT_ID, PROJECT_ID, "VIDEO", "已删除视频", "/materials/deleted.mp4",
            "video/mp4", 2_000L, 1280, 720, "mp4", new BigDecimal("4"), "now()");
    }

    @Test
    void resolvesOwnedImageVideoAndAudioWithTrustedMetadata() {
        List<VideoTaskReferenceResolver.ResolvedReference> resolved = resolver.resolve(
            TENANT_ID,
            PROJECT_ID,
            List.of(
                reference("IMAGE", 1, "reference_image", "ASSET_VISUAL_VARIANT", 97111L, "图片1"),
                reference("VIDEO", 1, "reference_video", "VIDEO_MATERIAL", 97201L, "视频1"),
                reference("AUDIO", 1, "reference_audio", "AUDIO_MATERIAL", 97202L, "音频1")
            )
        );

        assertThat(resolved).hasSize(3);
        assertThat(resolved.get(0))
            .extracting(
                VideoTaskReferenceResolver.ResolvedReference::format,
                VideoTaskReferenceResolver.ResolvedReference::width,
                VideoTaskReferenceResolver.ResolvedReference::height,
                VideoTaskReferenceResolver.ResolvedReference::fileSize)
            .containsExactly("png", 720, 1280, 1000L);
        assertThat(resolved.get(1).durationSeconds()).isEqualByComparingTo("5.5");
        assertThat(resolved.get(2).format()).isEqualTo("mp3");
        assertThat(resolved).allSatisfy(item -> assertThat(item.providerUrl()).startsWith("https://cos.example/materials/"));
    }

    @Test
    void rejectsMissingDeletedAndCrossTenantSources() {
        assertThatThrownBy(() -> resolver.resolve(TENANT_ID, PROJECT_ID, List.of(
            reference("VIDEO", 1, "reference_video", "VIDEO_MATERIAL", 99999L, "视频1"))))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.resolve(TENANT_ID, PROJECT_ID, List.of(
            reference("VIDEO", 1, "reference_video", "VIDEO_MATERIAL", 97203L, "视频1"))))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.resolve(TENANT_ID, PROJECT_ID, List.of(
            reference("VIDEO", 1, "reference_video", "VIDEO_MATERIAL", 97204L, "视频1"))))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.resolve(TENANT_ID, PROJECT_ID, List.of(
            reference("IMAGE", 1, "reference_image", "ASSET_VISUAL_VARIANT", 99998L, "图片1"))))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void sendsBoundCharacterAudioAndFreezesOverridesIntoTheActualProviderRequest() throws Exception {
        org.mockito.Mockito.when(projectPermissions.require(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new com.antshorttv.security.TenantContext(1L, TENANT_ID, 1L, "OWNER"));
        jdbc.update("""
            insert into character_asset(id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at)
            values (97112,?,?,'陈志国','LEAD','CONFIRMED',1,now(),now())
            """, TENANT_ID, PROJECT_ID);
        jdbc.update("update asset_visual_variant set name='常态' where id=97111");
        jdbc.update("""
            insert into storyboard(id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at)
            values (97402,?,?,1,2,'角色说话','DRAFT',1,now(),now())
            """, TENANT_ID, PROJECT_ID);
        jdbc.update("""
            insert into storyboard_asset_reference
              (tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id,reference_role,sort_order,resolution_status,source_type,source_name,locked_by_user,created_by,created_at,updated_at)
            values (?,?,97402,'CHARACTER',97112,97111,'MAIN',0,'RESOLVED','MANUAL','陈志国',true,1,now(),now())
            """, TENANT_ID, PROJECT_ID);
        for (long id : java.util.List.of(97410L, 97411L)) {
            long sourceProject = id == 97410 ? PROJECT_ID : PROJECT_ID + 1;
            jdbc.update("""
                insert into project_audio_asset
                  (id,tenant_id,project_id,name,upload_session_token,storage_path,mime_type,file_size,duration_seconds,status,created_by,created_at,updated_at)
                values (?,?,?,'角色声音',?,?,'audio/wav',1000,3,'ACTIVE',1,now(),now())
                """, id, TENANT_ID, sourceProject, "voice-" + id,
                "materials/" + TENANT_ID + "/" + sourceProject + "/voices/" + id + ".wav");
        }
        jdbc.update("""
            insert into character_voice_reference
              (tenant_id,project_id,character_id,source_project_id,resource_type,resource_id,updated_by,created_at,updated_at)
            values (?,?,97112,?,'PROJECT_AUDIO_ASSET',97410,1,now(),now())
            """, TENANT_ID, PROJECT_ID, PROJECT_ID);
        var model = new com.antshorttv.ai.AiModelEntity(); model.setId(99L); model.setModelCode("ep-character-voice");
        model.setConfigJson(jdbc.queryForObject("select config_json from ai_model where code='SEEDANCE_2_0_MINI'", String.class));
        var project = new com.antshorttv.project.ProjectEntity(); project.aspectRatio="9:16"; project.videoResolution="720p";
        var storyboard = new com.antshorttv.script.StoryboardEntity();
        storyboard.id=97402L; storyboard.tenantId=TENANT_ID; storyboard.projectId=PROJECT_ID;
        var request = new CreateAiVideoTaskRequest(storyboard.id,99L,"角色说话",null,null,null,null,null,
            5,"9:16","720p",true,false,null,null,null,null,null);
        Object target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(videoTasks);
        Object prepared = org.springframework.test.util.ReflectionTestUtils.invokeMethod(target,"prepareSeedanceRequest",project,storyboard,
            new com.antshorttv.ai.AiModelRoute(model,null,null,null),request);
        var task = new AiVideoTaskEntity(); task.prompt="角色说话"; task.generateAudio=true; task.durationSeconds=5; task.aspectRatio="9:16"; task.resolution="720p";
        task.compiledPrompt=org.springframework.test.util.ReflectionTestUtils.invokeMethod(prepared,"compiledPrompt");
        task.requestSnapshotJson=org.springframework.test.util.ReflectionTestUtils.invokeMethod(prepared,"snapshotJson");
        assertThat(task.compiledPrompt).contains("陈志国", "常态", "使用音频1作为音色参考");
        assertThat(task.requestSnapshotJson).contains("characterVoices", "97410");

        jdbc.update("update storyboard_asset_reference set resolution_status='ASSET_PENDING' where storyboard_id=97402");
        var skipMissing = new CreateAiVideoTaskRequest(storyboard.id,99L,"角色说话",null,null,null,null,null,
            5,"9:16","720p",true,false,null,null,null,false,true);
        Object audioOnly = org.springframework.test.util.ReflectionTestUtils.invokeMethod(target,"prepareSeedanceRequest",project,storyboard,
            new com.antshorttv.ai.AiModelRoute(model,null,null,null),skipMissing);
        String audioOnlySnapshot=org.springframework.test.util.ReflectionTestUtils.invokeMethod(audioOnly,"snapshotJson");
        var kept=new com.fasterxml.jackson.databind.ObjectMapper().readTree(audioOnlySnapshot).path("references");
        assertThat(kept.size()).isEqualTo(1);
        assertThat(kept.get(0).path("reference").path("mediaType").asText()).isEqualTo("AUDIO");
        assertThat(audioOnlySnapshot).contains("97410", "IMAGE_UNAVAILABLE");
        jdbc.update("update storyboard_asset_reference set resolution_status='RESOLVED' where storyboard_id=97402");

        jdbc.update("""
            insert into storyboard_character_voice
              (tenant_id,project_id,storyboard_id,character_id,variant_id,source_project_id,resource_type,resource_id,updated_by,created_at,updated_at)
            values (?,?,97402,97112,97111,?,'PROJECT_AUDIO_ASSET',97411,1,now(),now())
            """, TENANT_ID,PROJECT_ID,PROJECT_ID+1);
        Object overridden = org.springframework.test.util.ReflectionTestUtils.invokeMethod(target,"prepareSeedanceRequest",project,storyboard,
            new com.antshorttv.ai.AiModelRoute(model,null,null,null),request);
        String updatedSnapshot=org.springframework.test.util.ReflectionTestUtils.invokeMethod(overridden,"snapshotJson");
        assertThat(updatedSnapshot).contains("97411.wav", "\"overridden\":true").doesNotContain("97410.wav");
        assertThat(jdbc.queryForObject("select resource_id from character_voice_reference where tenant_id=? and project_id=? and character_id=97112",
            Long.class,TENANT_ID,PROJECT_ID)).isEqualTo(97410L);

        var body = new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v3/contents/generations/tasks", exchange -> {
            body.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] response="{\"id\":\"voice-task\",\"status\":\"queued\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            try (var output=exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var codec=new com.antshorttv.ai.AiSecretCodec("character-voice-test-key");
            var config=new com.antshorttv.ai.AiProviderConfigEntity();
            config.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v3"); config.setApiKeyCipher(codec.encrypt("test-key"));
            new SeedanceArkVideoProviderAdapter(codec,json).submit(config,model,task,"voice-request");
            var content=body.get().path("content");
            assertThat(content.get(0).path("text").asText()).contains("陈志国", "音频1");
            assertThat(content.get(2).path("type").asText()).isEqualTo("audio_url");
            assertThat(content.get(2).path("role").asText()).isEqualTo("reference_audio");
            assertThat(content.get(2).path("audio_url").path("url").asText()).contains("97410.wav").doesNotContain("97411.wav","/playback");
        } finally { server.stop(0); }
    }

    @Test
    void multimodalReferencesKeepBrowserIdentityWhileVideoUsesAnExtractedCover() {
        String original = "materials/97001/97002/images/202610/97101/result-97101/original.png";
        String browserDisplay = "/api/projects/97002/ai-image-results/97101/file";
        String providerOriginal = "https://cos.example/" + original + "?sign=original-only";
        jdbc.update("update ai_image_result set storage_path = ?, image_url = ? where id = 97101", original, browserDisplay);
        when(objectStorageService.modelAccessUrl(org.mockito.ArgumentMatchers.eq(original), any(Duration.class)))
            .thenReturn(providerOriginal);
        com.antshorttv.ai.AiModelEntity model = new com.antshorttv.ai.AiModelEntity();
        model.setId(99L);
        model.setConfigJson(jdbc.queryForObject("select config_json from ai_model where code = 'SEEDANCE_2_0_MINI'", String.class));
        com.antshorttv.project.ProjectEntity project = new com.antshorttv.project.ProjectEntity();
        project.aspectRatio = "9:16";
        project.videoResolution = "720p";
        com.antshorttv.script.StoryboardEntity storyboard = new com.antshorttv.script.StoryboardEntity();
        storyboard.id = 97401L;
        storyboard.tenantId = TENANT_ID;
        storyboard.projectId = PROJECT_ID;
        storyboard.promptDocumentJson = """
            {"version":2,"nodes":[{"type":"text","text":"Frame "},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT",
               "sourceId":97111,"assetType":"CHARACTER","assetId":97112,"variantId":97111,"displayName":"Frame"}]}
            """;
        Object serviceTarget = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(videoTasks);
        Object prepared = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            serviceTarget, "prepareSeedanceRequest", project, storyboard,
            new com.antshorttv.ai.AiModelRoute(model, null, null, null),
            new CreateAiVideoTaskRequest(storyboard.id, 99L, "Prompt", null, null, null, null, null,
                5, "9:16", "720p", true, false, null, null, null, null, null));
        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.tenantId = TENANT_ID;
        task.projectId = PROJECT_ID;
        task.storyboardId = storyboard.id;
        task.modelId = 99L;
        task.providerCode = "VOLCENGINE_ARK";
        task.model = "endpoint-test";
        task.prompt = "Prompt";
        task.compiledPrompt = "Prompt";
        task.firstFrameUrl = org.springframework.test.util.ReflectionTestUtils.invokeMethod(prepared, "firstFrameUrl");
        task.requestSnapshotJson = org.springframework.test.util.ReflectionTestUtils.invokeMethod(prepared, "snapshotJson");
        task.durationSeconds = 5;
        task.aspectRatio = "9:16";
        task.resolution = "720p";
        task.status = "GENERATING";
        task.createdBy = 1L;
        task.createdAt = java.time.LocalDateTime.now();
        task.updatedAt = task.createdAt;
        videoTaskMapper.insert(task);
        String capturedCover = "/api/projects/97002/ai-image-results/97501/display";
        when(videoCovers.prepare(any(), any())).thenAnswer(call -> {
            call.getArgument(1, AiVideoResultEntity.class).coverUrl = capturedCover;
            return new com.antshorttv.aiimage.AiImageResultEntity();
        });
        Object preparedResult = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            serviceTarget, "prepareGeneratedResult", task,
            new AiVideoProviderAdapter.VideoResult("SUCCEEDED", null, null));
        AiVideoResultEntity generated = org.springframework.test.util.ReflectionTestUtils.invokeMethod(preparedResult, "video");
        videoResultMapper.insert(generated);

        assertThat(jdbc.queryForObject("select first_frame_url from ai_video_task where id = ?", String.class, task.id))
            .isEqualTo(browserDisplay);
        assertThat(jdbc.queryForObject("select cover_url from ai_video_result where task_id = ?", String.class, task.id))
            .isEqualTo(capturedCover);
        Object content = org.springframework.test.util.ReflectionTestUtils.invokeMethod(seedance, "content", task);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content).toString())
            .contains(providerOriginal);
    }

    private StoryboardPromptCompiler.Reference reference(
        String mediaType,
        int mediaIndex,
        String role,
        String sourceType,
        Long sourceId,
        String label
    ) {
        return new StoryboardPromptCompiler.Reference(
            mediaType, mediaIndex, label, role, sourceType, sourceId, null, null, null, label, mediaIndex - 1);
    }

    private void insertMaterial(
        Long id,
        Long tenantId,
        Long projectId,
        String type,
        String name,
        String path,
        String mimeType,
        Long fileSize,
        Integer width,
        Integer height,
        String format,
        BigDecimal duration,
        String deletedAt
    ) {
        jdbc.update("""
            insert into material
              (id, tenant_id, project_id, material_type, source_type, name, url, storage_path,
               mime_type, file_size, width, height, duration_seconds, fps, format, status,
               created_by, created_at, updated_at, deleted_at)
            values (?, ?, ?, ?, 'UPLOADED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, now(), now(), %s)
            """.formatted(deletedAt == null ? "null" : deletedAt),
            id, tenantId, projectId, type, name, path, path, mimeType, fileSize, width, height, duration,
            "VIDEO".equals(type) ? BigDecimal.valueOf(24) : null, format);
    }
}
