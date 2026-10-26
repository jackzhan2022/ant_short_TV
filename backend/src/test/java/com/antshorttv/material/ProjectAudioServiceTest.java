package com.antshorttv.material;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.common.*;
import com.antshorttv.project.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.AssetNormalizationGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.*;
import com.qcloud.cos.COS;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ProjectAudioServiceTest {
    private JdbcTemplate jdbc;
    private final ProjectAccessResolver access = mock(ProjectAccessResolver.class);
    private final MediaUploadSessionService uploads = mock(MediaUploadSessionService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
    private final COS cos = mock(COS.class, RETURNS_DEEP_STUBS);
    private final AssetNormalizationGuard normalization = mock(AssetNormalizationGuard.class);
    private ProjectAudioService service;
    private StoryboardCharacterVoiceService storyboardVoices;

    @BeforeEach
    void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:audio_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V151__project_audio_assets_and_character_voices.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V152__storyboard_character_voice_overrides.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V163__system_audio_library.sql")).execute(dataSource);
        jdbc.execute("create table project(id bigint primary key,tenant_id bigint,name varchar(50),deleted_at timestamp)");
        jdbc.execute("create table character_asset(id bigint primary key,tenant_id bigint,project_id bigint,merge_target_id bigint,deleted_at timestamp,name varchar(50))");
        jdbc.execute("create table storyboard(id bigint primary key,tenant_id bigint,project_id bigint,deleted_at timestamp)");
        jdbc.execute("create table storyboard_asset_reference(id bigint,tenant_id bigint,project_id bigint,storyboard_id bigint,asset_type varchar(20),asset_id bigint,variant_id bigint,reference_role varchar(20),sort_order int,retired_at timestamp)");
        jdbc.execute("create table asset_visual_variant(id bigint,tenant_id bigint,project_id bigint,name varchar(50),deleted_at timestamp)");
        jdbc.update("insert into project values (2,1,'当前项目',null),(3,1,'来源项目',null),(4,9,'其他团队',null)");
        jdbc.update("insert into character_asset values (10,1,2,null,null,'角色甲'),(11,1,3,null,null,'角色乙')");
        jdbc.update("insert into storyboard values (20,1,2,null),(21,1,2,null)");
        jdbc.update("insert into asset_visual_variant values (100,1,2,'日常',null),(101,1,2,'老年',null)");
        jdbc.update("insert into storyboard_asset_reference values (1,1,2,20,'CHARACTER',10,100,'MAIN',0,null),(2,1,2,21,'CHARACTER',10,100,'MAIN',0,null),(3,1,2,20,'CHARACTER',10,101,'VISIBLE',1,null)");
        when(access.requireView(anyLong(), anyLong())).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "denied"));
        grant(1, 2); grant(1, 3);
        var properties = new ObjectStorageProperties(); properties.setBucket("test-bucket");
        service = new ProjectAudioService(jdbc, new ProjectPermissionGuard(access), uploads, storage, grants, properties, cos, normalization);
        storyboardVoices = new StoryboardCharacterVoiceService(jdbc, new ProjectPermissionGuard(access), service);
        byte[] wav = "RIFF1234WAVEdata".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(uploads.requireCompleted(99L, 1L, 2L, "upload"))
            .thenReturn(new VerifiedMediaUpload("upload", "materials/1/2/uploads/audio.wav", "audio/wav", wav.length, "etag"));
        when(storage.resource("materials/1/2/uploads/audio.wav")).thenReturn(new ByteArrayResource(wav));
        when(cos.generateMediainfo(any()).getMediaInfo().getStream().getMediaInfoAudioList().get(0).getDuration()).thenReturn("12.5");
        clearInvocations(cos);
    }

    private void grant(long tenant, long project) {
        var entity = new ProjectEntity(); entity.id = project; entity.tenantId = tenant;
        doReturn(new ProjectAccessContext(
            new TenantContext(99L, tenant, 98L, "MEMBER"), entity, ProjectAccessSource.PROJECT_MEMBER,
            null, null, Set.of("MENU:PROJECTS", "MENU:ASSET_LIBRARY"), null)).when(access).requireView(tenant, project);
    }
    private void audio(long id, long tenant, long project) {
        jdbc.update("""
            insert into project_audio_asset(tenant_id,project_id,id,name,upload_session_token,storage_path,mime_type,
              file_size,duration_seconds,status,created_by,created_at,updated_at)
            values (?,?,?,'参考音频',? ,?,'audio/wav',100,12,'ACTIVE',99,now(),now())
            """, tenant, project, id, "upload-" + id, "materials/" + tenant + "/" + project + "/audio.wav");
    }

    @Test
    void systemAudioBindsToCharactersAndStoryboardShapesAndRemainsUsableByVideoPreparation() {
        String path = "materials/0/system_audio/202610/" + "a".repeat(64) + "/v1/original.wav";
        jdbc.update("""
            insert into system_audio_asset(id,name,source_path,content_sha256,storage_path,mime_type,file_size,duration_seconds,status,created_at,updated_at)
            values (71,'系统男声','男/男声.wav',?,?,'audio/wav',1024,12,'ACTIVE',now(),now())
            """, "a".repeat(64), path);
        var saved = service.bind(1L, 2L, 10L, 2L, "SYSTEM_AUDIO_ASSET", 71L);
        assertThat(saved.sourceProjectName()).isEqualTo("系统音频");
        assertThat(saved.previewUrl()).isEqualTo("/api/system-audio/71/playback");
        assertThat(service.current(1L, 2L, 10L)).isEqualTo(saved);
        assertThat(storyboardVoices.current(1L, 2L, 20L, 10L, 100L).audio()).isEqualTo(saved);
        assertThat(storyboardVoices.bind(1L, 2L, 20L, 10L, 100L, 2L, "SYSTEM_AUDIO_ASSET", 71L).overridden()).isTrue();
        var media = service.trustedMedia(1L, 2L, "SYSTEM_AUDIO_ASSET", 71L);
        assertThat(media.storagePath()).isEqualTo(path);
        assertThat(media.format()).isEqualTo("wav");
        assertThatThrownBy(() -> service.bind(1L, 2L, 10L, 4L, "SYSTEM_AUDIO_ASSET", 71L)).hasMessage("denied");
        jdbc.update("update system_audio_asset set storage_path='materials/1/2/private.wav' where id=71");
        assertThatThrownBy(() -> service.trustedMedia(1L, 2L, "SYSTEM_AUDIO_ASSET", 71L)).isInstanceOf(BusinessException.class);
        jdbc.update("update system_audio_asset set storage_path=?,status='INACTIVE' where id=71", path);
        assertThat(service.current(1L, 2L, 10L)).isNull();
    }

    @Test
    void uploadsOnceAndRestoresTheSavedCharacterReference() throws Exception {
        assertThat(service.current(1L, 2L, 10L)).isNull();
        var audio = service.upload(1L, 2L, "upload", "陈志国.wav");
        assertThat(audio.durationSeconds()).isEqualByComparingTo("12.5");
        assertThat(service.upload(1L, 2L, "upload", "陈志国.wav").resourceId()).isEqualTo(audio.resourceId());
        verify(cos, times(1)).generateMediainfo(any());
        service.bind(1L, 2L, 10L, 2L, audio.resourceType(), audio.resourceId());
        assertThat(service.current(1L, 2L, 10L)).isEqualTo(audio);
        assertThat(jdbc.queryForObject("select count(*) from project_audio_asset", Integer.class)).isEqualTo(1);
    }

    @Test
    void replacesCrossProjectVoiceAndRejectsForgedScopes() {
        audio(21, 1, 2); audio(22, 1, 3); audio(23, 9, 4);
        service.bind(1L, 2L, 10L, 2L, "PROJECT_AUDIO_ASSET", 21L);
        var saved = service.bind(1L, 2L, 10L, 3L, "PROJECT_AUDIO_ASSET", 22L);
        assertThat(saved.sourceProjectName()).isEqualTo("来源项目");
        assertThat(jdbc.queryForObject("select count(*) from character_voice_reference", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.bind(1L, 2L, 10L, 4L, "PROJECT_AUDIO_ASSET", 23L)).hasMessage("denied");
        assertThatThrownBy(() -> service.bind(1L, 2L, 10L, 2L, "PROJECT_AUDIO_ASSET", 22L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.bind(1L, 2L, 11L, 2L, "PROJECT_AUDIO_ASSET", 21L)).hasMessage("角色不存在。");
        assertThat(service.current(1L, 2L, 10L)).isEqualTo(saved);
        jdbc.update("update project_audio_asset set status='DELETED' where id=22");
        assertThat(service.current(1L, 2L, 10L)).isNull();
    }

    @Test
    void nativePlaybackResolvesProjectScopeAndRequiresAccessBeforeIssuingAGrant() {
        audio(21, 1, 2);
        when(grants.issue(any())).thenReturn(new DeliveryGrant("https://cdn.example/audio.wav", Instant.now().plusSeconds(60)));
        assertThat(service.playback(2L, "PROJECT_AUDIO_ASSET", 21L).url()).contains("cdn.example");
        verify(grants).issue(argThat(request -> request.tenantId().equals(1L) && request.projectId().equals(2L)
            && request.objectKey().equals("materials/1/2/audio.wav") && request.renditionType().equals("AUDIO")));
        clearInvocations(grants);
        when(access.requireView(1L, 2L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "denied"));
        assertThatThrownBy(() -> service.playback(2L, "PROJECT_AUDIO_ASSET", 21L)).hasMessage("denied");
        verifyNoInteractions(grants);
    }

    @Test
    void rejectsMislabeledOversizedAndForeignUploadsBeforePersisting() {
        when(storage.resource(anyString())).thenReturn(new ByteArrayResource("not audio".getBytes()));
        assertThatThrownBy(() -> service.upload(1L, 2L, "upload", "bad.wav")).isInstanceOf(BusinessException.class);
        when(uploads.requireCompleted(99L, 1L, 2L, "upload")).thenReturn(new VerifiedMediaUpload("upload", "key", "audio/wav", 11 * 1024 * 1024, "etag"));
        assertThatThrownBy(() -> service.upload(1L, 2L, "upload", "large.wav")).isInstanceOf(BusinessException.class);
        when(uploads.requireCompleted(99L, 1L, 2L, "upload")).thenThrow(new IllegalArgumentException("scope"));
        assertThatThrownBy(() -> service.upload(1L, 2L, "upload", "foreign.wav")).hasMessage("scope");
        assertThat(jdbc.queryForObject("select count(*) from project_audio_asset", Integer.class)).isZero();
    }

    @Test
    void storyboardOverridesAreIsolatedByShotAndShapeAndResetToTheLatestDefault() {
        audio(21, 1, 2); audio(22, 1, 3); audio(24, 1, 2);
        service.bind(1L, 2L, 10L, 2L, "PROJECT_AUDIO_ASSET", 21L);
        var inherited = storyboardVoices.current(1L, 2L, 20L, 10L, 100L);
        assertThat(inherited.overridden()).isFalse();
        assertThat(inherited.audio().resourceId()).isEqualTo(21L);
        storyboardVoices.bind(1L, 2L, 20L, 10L, 100L, 3L, "PROJECT_AUDIO_ASSET", 22L);
        assertThat(storyboardVoices.current(1L, 2L, 20L, 10L, 100L).audio().resourceId()).isEqualTo(22L);
        assertThat(storyboardVoices.current(1L, 2L, 20L, 10L, 101L).audio().resourceId()).isEqualTo(21L);
        assertThat(storyboardVoices.current(1L, 2L, 21L, 10L, 100L).audio().resourceId()).isEqualTo(21L);
        assertThat(service.current(1L, 2L, 10L).resourceId()).isEqualTo(21L);
        service.bind(1L, 2L, 10L, 2L, "PROJECT_AUDIO_ASSET", 24L);
        assertThat(storyboardVoices.current(1L, 2L, 20L, 10L, 100L).audio().resourceId()).isEqualTo(22L);
        assertThat(storyboardVoices.current(1L, 2L, 21L, 10L, 100L).audio().resourceId()).isEqualTo(24L);
        assertThatThrownBy(() -> storyboardVoices.bind(1L, 2L, 20L, 10L, 102L, 2L, "PROJECT_AUDIO_ASSET", 21L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storyboardVoices.bind(1L, 2L, 20L, 11L, 100L, 2L, "PROJECT_AUDIO_ASSET", 21L)).isInstanceOf(BusinessException.class);
        var reset = storyboardVoices.reset(1L, 2L, 20L, 10L, 100L);
        assertThat(reset.overridden()).isFalse();
        assertThat(reset.audio().resourceId()).isEqualTo(24L);
        assertThat(service.current(1L, 2L, 10L).resourceId()).isEqualTo(24L);
        assertThat(jdbc.queryForObject("select count(*) from storyboard_character_voice", Integer.class)).isZero();
        jdbc.update("update storyboard_asset_reference set retired_at=now() where storyboard_id=20");
        assertThat(storyboardVoices.choicesForVideo(1L, 2L, 20L)).isEmpty();
    }

    @Test
    void normalizationBlocksChangesAndStorageKeysCannotEscapeTheSourceProject() {
        audio(21, 1, 2);
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "normalizing")).when(normalization).requireReady(1, 2);
        assertThatThrownBy(() -> service.bind(1L, 2L, 10L, 2L, "PROJECT_AUDIO_ASSET", 21L)).hasMessage("normalizing");
        jdbc.update("update project_audio_asset set storage_path='materials/1/3/audio.wav' where id=21");
        assertThatThrownBy(() -> service.reference(1L, 2L, "PROJECT_AUDIO_ASSET", 21L)).isInstanceOf(BusinessException.class);
    }
}
