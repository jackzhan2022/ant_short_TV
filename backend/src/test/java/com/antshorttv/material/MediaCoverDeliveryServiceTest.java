package com.antshorttv.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.antshorttv.aiimage.AiImageResultEntity;
import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageProperties;
import com.antshorttv.storage.RegisteredMediaDetails;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MediaCoverDeliveryServiceTest {
    @Test
    void displaysDraftAndConfirmedStoryboardFramesWithoutTenantHeader() throws Exception {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
        f.jdbc.execute("create table storyboard (id bigint,tenant_id bigint,project_id bigint,first_frame_url varchar(1000),status varchar(32),deleted_at timestamp)");
        f.jdbc.update("insert into storyboard values (7,11,22,?,'DRAFT',null)", f.originalKey);
        f.register("ORIGINAL", f.originalKey);
        when(f.renditions.displayDetails(f.identity)).thenReturn(f.display("READY"));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MediaCoverDeliveryController(f.service)).build();
        for (String state : new String[] {"DRAFT", "CONFIRMED"}) {
            f.jdbc.update("update storyboard set status = ?", state);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/projects/22/storyboards/7/first-frame"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "https://cdn.example/display.jpg"));
        }
        verify(f.permissions, org.mockito.Mockito.times(2)).require(11L, 22L, "STORYBOARD:VIEW");
    }

    @Test
    void reusesReadyCompressedRegistryIdentityForOwnedOriginalAndConfiguredCdn() {
        for (var kind : MediaPlaybackService.ResourceKind.values()) {
            Fixture f = new Fixture(kind);
            f.register("ORIGINAL", f.originalKey);
            when(f.renditions.displayDetails(f.identity)).thenReturn(f.display("READY"));
            var first = f.service.delivery(22L, 1L, kind);
            assertThat(first).isNotNull();
            assertThat(first.url()).isEqualTo("https://cdn.example/display.jpg");
            f.source("https://antvcdn.aixmax.cn/" + f.originalKey + "?sign=old&t=1");
            assertThat(f.service.delivery(22L, 1L, kind)).isNotNull();
            var request = ArgumentCaptor.forClass(DeliveryGrantRequest.class);
            verify(f.grants, org.mockito.Mockito.times(2)).issue(request.capture());
            assertThat(request.getValue().objectKey()).isEqualTo(f.displayKey);
            assertThat(request.getValue().video()).isFalse();
            verify(f.renditions, never()).retryFailedDisplay(any(), anyString());
        }
    }

    @Test
    void alreadyRegisteredDisplayIsReusedWithoutRecompression() {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.SHOT_COMPOSE_RESULT);
        f.register("DISPLAY_IMAGE_SLIM", f.displayKey);
        f.source(f.displayKey);
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNotNull();
        verifyNoInteractions(f.renditions);
    }

    @Test
    void resolvesActiveOwnedAiImageAndRefusesForeignOrDeletedImageSources() {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
        f.source("/api/projects/22/ai-image-results/44/download");
        var image = new AiImageResultEntity();
        image.setId(44L); image.setTenantId(11L); image.setProjectId(22L); image.setStatus("ACTIVE");
        when(f.images.selectById(44L)).thenReturn(image);
        var identity = new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 44L, "result-44");
        when(f.renditions.displayDetails(identity)).thenReturn(new RegisteredMediaDetails(44L, identity,
            "DISPLAY_IMAGE_SLIM", f.displayKey, "image/jpeg", 50, "READY", null));
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNotNull();
        image.setTenantId(12L);
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNull();
        image.setTenantId(11L); image.setStatus("DELETED");
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNull();
    }

    @Test
    void missingOrUnknownSourcesStayEmptyAndNeverProxyOriginals() {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.EPISODE_VIDEO_VERSION);
        for (String source : new String[] {null, "", "https://external.example/cover.jpg", "data:image/png;base64,secret",
            "materials/11/23/foreign/original.jpg", f.originalKey}) {
            f.source(source);
            assertThat(f.service.delivery(22L, 1L, f.kind)).isNull();
        }
        verifyNoInteractions(f.grants, f.renditions);
    }

    @Test
    void submitsMissingOrFailedDisplayAndLeavesPendingDisplayAlone() {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
        f.register("ORIGINAL", f.originalKey);
        when(f.renditions.originalDetails(f.identity)).thenReturn(new RegisteredMediaDetails(1L, f.identity,
            "ORIGINAL", f.originalKey, "image/jpeg", 100, "READY", null));
        when(f.renditions.displayDetails(f.identity)).thenReturn(null, f.display("PENDING"), f.display("FAILED"));
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNull();
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNull();
        assertThat(f.service.delivery(22L, 1L, f.kind)).isNull();
        verify(f.renditions, org.mockito.Mockito.times(2)).retryFailedDisplay(org.mockito.ArgumentMatchers.eq(f.identity), anyString());
        verifyNoInteractions(f.grants);
    }

    @Test
    void enforcesResultOwnershipPermissionAndRetirement() {
        Fixture f = new Fixture(MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
        assertThatThrownBy(() -> f.service.delivery(23L, 1L, f.kind)).isInstanceOf(BusinessException.class);
        f.jdbc.update("update " + f.table + " set status = 'DELETED'");
        assertThatThrownBy(() -> f.service.delivery(22L, 1L, f.kind)).isInstanceOf(BusinessException.class);
        f.jdbc.update("update " + f.table + " set status = 'ACTIVE'");
        when(f.permissions.require(anyLong(), anyLong(), anyString())).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Denied"));
        assertThatThrownBy(() -> f.service.delivery(22L, 1L, f.kind)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(f.grants);
        Fixture retired = new Fixture(f.kind);
        retired.register("ORIGINAL", retired.originalKey);
        retired.jdbc.update("update media_object set status = 'RETIRED'");
        assertThat(retired.service.delivery(22L, 1L, retired.kind)).isNull();
        verifyNoInteractions(retired.grants, retired.renditions);
    }

    private static class Fixture {
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
        final MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
        final ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
        final AiImageResultMapper images = mock(AiImageResultMapper.class);
        final MediaPlaybackService.ResourceKind kind;
        final String table;
        final String originalKey = "materials/11/22/images/202610/99/v1/original.jpg";
        final String displayKey = "materials/11/22/images/202610/99/v1/derived/display-image-slim.jpg";
        final MediaObjectIdentity identity = new MediaObjectIdentity(11L, 22L, "AI_IMAGE_RESULT", 99L, "v1");
        final MediaCoverDeliveryService service;
        Fixture(MediaPlaybackService.ResourceKind kind) {
            this.kind = kind; table = kind.name().toLowerCase();
            jdbc.execute("create table " + table + " (id bigint primary key,tenant_id bigint,project_id bigint,cover_url varchar(1000),status varchar(32))");
            jdbc.execute("create table media_object (id bigint,tenant_id bigint,project_id bigint,asset_type varchar(64),asset_id bigint,version_id varchar(64),rendition_type varchar(32),object_key varchar(512),mime_type varchar(128),file_size bigint,status varchar(32))");
            jdbc.update("insert into " + table + " values (1,11,22,?,'ACTIVE')", originalKey);
            when(permissions.require(anyLong(), anyLong(), anyString())).thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
            when(grants.issue(any())).thenReturn(new DeliveryGrant("https://cdn.example/display.jpg", Instant.now()));
            service = new MediaCoverDeliveryService(jdbc, permissions, grants, renditions, images, new ObjectStorageKeyFactory(), new ObjectStorageProperties());
        }
        void source(String source) { jdbc.update("update " + table + " set cover_url = ?", source); }
        void register(String type, String key) {
            jdbc.update("insert into media_object values (1,11,22,'AI_IMAGE_RESULT',99,'v1',?,?, 'image/jpeg',100,'READY')", type, key);
        }
        RegisteredMediaDetails display(String status) {
            return new RegisteredMediaDetails(2L, identity, "DISPLAY_IMAGE_SLIM", displayKey, "image/jpeg", 50, status, null);
        }
    }
}
