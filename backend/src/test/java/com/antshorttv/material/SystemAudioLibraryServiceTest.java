package com.antshorttv.material;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.storage.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SystemAudioLibraryServiceTest {
    private JdbcTemplate jdbc;
    private final CurrentPrincipal principal = mock(CurrentPrincipal.class);
    private final MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
    private SystemAudioLibraryService service;

    @BeforeEach
    void setup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:system_audio_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V163__system_audio_library.sql")).execute(dataSource);
        when(principal.require()).thenReturn(new AuthenticatedUser(99L, "test", "session", LocalDateTime.now().plusDays(1)));
        service = new SystemAudioLibraryService(jdbc, principal, grants);
        audio(1, "清朗少年", "男声", "少年", "ACTIVE");
        audio(2, "温柔女声", "女声", "青年", "ACTIVE");
        audio(3, "沉稳男声", "男声", "中年", "ACTIVE");
        audio(4, "停用音色", "女声", "青年", "INACTIVE");
    }

    private void audio(long id, String name, String gender, String age, String status) {
        jdbc.update("""
            insert into system_audio_asset(id,name,category,gender,age_group,source_path,content_sha256,
              storage_path,mime_type,file_size,duration_seconds,status,created_at,updated_at)
            values (?,?,'古风',?,?,'男/古风/样本.wav',?,?, 'audio/wav',1024,12.5,?,now(),now())
            """, id, name, gender, age, String.format("%064d", id),
            "materials/0/system_audio/202610/" + String.format("%064d", id) + "/v1/original.wav", status);
    }

    @Test
    void filtersAndPagesOnlyActiveAudioWithoutLeakingStorageKeys() {
        var page = service.list(null, null, null, 2, 2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.data()).extracting(SystemAudioLibraryService.Audio::name).containsExactly("沉稳男声");
        assertThat(page.data().get(0).previewUrl()).isEqualTo("/api/system-audio/3/playback");
        assertThat(service.list("少年", "男声", "少年", 1, 12).data())
            .extracting(SystemAudioLibraryService.Audio::id).containsExactly(1L);
        assertThat(service.list("古风", null, null, 1, 12).total()).isEqualTo(3);
        assertThat(service.list("' OR 1=1 --", null, null, 1, 12).total()).isZero();
        assertThat(service.list(null, null, null, -1, 1000).pageSize()).isEqualTo(100);
    }

    @Test
    void playbackRequiresLoginAndIssuesAUserBoundSystemGrant() {
        when(grants.issue(any())).thenReturn(new DeliveryGrant("https://cdn.example/audio", Instant.now().plusSeconds(60)));
        assertThat(service.playback(1L).url()).isEqualTo("https://cdn.example/audio");
        verify(grants).issue(argThat(request -> request.tenantId().equals(0L) && request.projectId() == null
            && request.userId().equals(99L) && request.resourceType().equals("SYSTEM_AUDIO_ASSET")
            && request.objectKey().startsWith("materials/0/system_audio/")));
        clearInvocations(grants);
        when(principal.require()).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录。"));
        assertThatThrownBy(() -> service.playback(1L)).hasMessage("请先登录。");
        assertThatThrownBy(() -> service.list(null, null, null, 1, 12)).hasMessage("请先登录。");
        verifyNoInteractions(grants);
    }

    @Test
    void rejectsRetiredMissingAndForgedStorageObjectsBeforeSigning() {
        assertThatThrownBy(() -> service.playback(4L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.playback(999L)).isInstanceOf(BusinessException.class);
        jdbc.update("update system_audio_asset set storage_path='materials/1/2/private.wav' where id=1");
        assertThatThrownBy(() -> service.playback(1L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(grants);
    }
}
