package com.antshorttv.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaPlaybackServiceTest {
    @Test
    void derivesTenantAndAuthorizesEachResourceBeforeIssuingVideoGrant() {
        for (var kind : MediaPlaybackService.ResourceKind.values()) {
            Fixture f = new Fixture(kind);
            String permission = switch (kind) {
                case AI_VIDEO_RESULT -> "AI_VIDEO_TASK:VIEW";
                case SHOT_COMPOSE_RESULT -> "SHOT_COMPOSE:VIEW";
                case EPISODE_VIDEO_VERSION -> "EPISODE_VERSION:VIEW";
            };
            when(f.permissions.require(11L, 22L, permission)).thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
            when(f.grants.issue(any())).thenReturn(new DeliveryGrant("https://cdn.example/play.mp4", Instant.now()));
            assertThat(f.service.issue(22L, 1L, kind).url()).isEqualTo("https://cdn.example/play.mp4");
            var request = ArgumentCaptor.forClass(DeliveryGrantRequest.class);
            verify(f.grants).issue(request.capture());
            assertThat(request.getValue().tenantId()).isEqualTo(11);
            assertThat(request.getValue().projectId()).isEqualTo(22);
            assertThat(request.getValue().userId()).isEqualTo(33);
            assertThat(request.getValue().video()).isTrue();
        }
    }

    @Test
    void refusesDeletedForeignProjectForeignObjectAndUnauthorizedResources() {
        for (var kind : MediaPlaybackService.ResourceKind.values()) {
            Fixture f = new Fixture(kind);
            assertThatThrownBy(() -> f.service.issue(23L, 1L, kind)).isInstanceOf(BusinessException.class);
            f.jdbc.update("update " + f.table + " set status = 'DELETED'");
            assertThatThrownBy(() -> f.service.issue(22L, 1L, kind)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(f.grants);
            f.jdbc.update("update " + f.table + " set status = 'ACTIVE', storage_path = 'materials/11/23/foreign.mp4'");
            assertThatThrownBy(() -> f.service.issue(22L, 1L, kind)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(f.grants);
        }
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
        when(f.permissions.require(11L, 22L, "AI_VIDEO_TASK:VIEW"))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Denied"));
        assertThatThrownBy(() -> f.service.issue(22L, 1L, MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(f.grants);
    }

    @Test
    void browserClickRedirectRequiresNoTenantHeaderAndCannotBeCached() throws Exception {
        var service = mock(MediaPlaybackService.class);
        when(service.issue(22L, 1L, MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT))
            .thenReturn(new DeliveryGrant("https://cdn.example/play.mp4", Instant.now()));
        MockMvcBuilders.standaloneSetup(new MediaPlaybackController(service)).build()
            .perform(get("/api/projects/22/ai-video-results/1/playback").header("Range", "bytes=0-99"))
            .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Location", "https://cdn.example/play.mp4"));
    }

    @Test
    void explicitVideoDownloadUsesDownloadPermissionAndAttachmentGrant() {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
        when(f.permissions.require(11L, 22L, "AI_VIDEO_RESULT:DOWNLOAD"))
            .thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
        when(f.grants.issue(any())).thenReturn(new DeliveryGrant("https://cdn.example/download.mp4", Instant.now()));

        assertThat(f.service.download(22L, 1L).url()).isEqualTo("https://cdn.example/download.mp4");
        verify(f.permissions).require(11L, 22L, "AI_VIDEO_RESULT:DOWNLOAD");
    }

    private static class Fixture {
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
        final MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
        final String table;
        final MediaPlaybackService service;
        Fixture(MediaPlaybackService.ResourceKind kind) {
            table = kind.name().toLowerCase();
            jdbc.execute("create table " + table + " (id bigint primary key,tenant_id bigint,project_id bigint,storage_path varchar(1000),status varchar(32))");
            jdbc.update("insert into " + table + " values (1,11,22,'materials/11/22/videos/v1/original.mp4','ACTIVE')");
            service = new MediaPlaybackService(jdbc, permissions, grants, new ObjectStorageKeyFactory());
        }
    }
}
