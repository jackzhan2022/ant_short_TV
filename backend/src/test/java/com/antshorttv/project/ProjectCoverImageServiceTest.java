package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.*;
import com.antshorttv.style.StyleLibraryEntity;
import com.antshorttv.style.StyleLibraryMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;

class ProjectCoverImageServiceTest {
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final ProjectAccessResolver access = mock(ProjectAccessResolver.class);
    private final ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final MediaDeliveryGrantService grants = mock(MediaDeliveryGrantService.class);
    private final StyleLibraryMapper styles = mock(StyleLibraryMapper.class);
    private final com.antshorttv.aiimage.AiImageResultMapper images = mock(com.antshorttv.aiimage.AiImageResultMapper.class);
    private final ProjectEntity project = new ProjectEntity();
    private final ProjectCoverImageService service = new ProjectCoverImageService(
        projects, access, renditions, storage, grants, styles, images,
        new ObjectStorageKeyFactory(), new ObjectStorageProperties()
    );
    private byte[] png;

    @BeforeEach
    void setUp() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png", output);
        png = output.toByteArray();
        project.id = 7L;
        project.tenantId = 3L;
        project.coverUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        when(projects.selectById(7L)).thenReturn(project);
        when(projects.claimCover(any(), anyString(), anyString(), anyBoolean())).thenReturn(1);
        when(access.requireView(3L, 7L)).thenReturn(new ProjectAccessContext(
            new TenantContext(9L, 3L, 1L, "OWNER"), project, ProjectAccessSource.TENANT_WIDE,
            null, null, Set.of("PROJECT:VIEW"), null
        ));
        when(storage.uploadOriginal(anyString(), any(byte[].class), anyString()))
            .thenAnswer(call -> new StoredObject(call.getArgument(0), png.length, "image/png", "etag", "INTELLIGENT_TIERING"));
        when(grants.issue(any())).thenReturn(new DeliveryGrant("https://cdn.example/compressed.png", Instant.now()));
    }

    @Test
    void convertsUploadedOriginalOnceAndDoesNotDeliverWhilePending() {
        assertThat(service.delivery(7L)).isNull();
        var identity = ArgumentCaptor.forClass(MediaObjectIdentity.class);
        verify(renditions).registerOriginalAndSubmit(identity.capture(), any(), eq(12), eq(8), anyString());
        assertThat(identity.getValue().assetType()).isEqualTo("PROJECT_COVER");
        verifyNoInteractions(grants);
        when(renditions.originalDetails(any())).thenReturn(new RegisteredMediaDetails(
            1L, identity.getValue(), "ORIGINAL", "original.png", "image/png", png.length, "READY", null
        ));
        service.delivery(7L);
        verify(storage, times(1)).uploadOriginal(anyString(), any(byte[].class), anyString());
        verify(renditions, times(1)).registerOriginalAndSubmit(any(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void deliversOnlyReadyCompressedRenditionWithProjectAuthorization() {
        when(renditions.displayDetails(any())).thenAnswer(call -> new RegisteredMediaDetails(
            2L, call.getArgument(0), "DISPLAY_IMAGE_SLIM", "derived/display.png", "image/png", 100, "READY", null
        ));
        assertThat(service.delivery(7L).url()).isEqualTo("https://cdn.example/compressed.png");
        var request = ArgumentCaptor.forClass(DeliveryGrantRequest.class);
        verify(grants).issue(request.capture());
        assertThat(request.getValue().objectKey()).isEqualTo("derived/display.png");
        assertThat(request.getValue().userId()).isEqualTo(9L);
        verify(access).requireView(3L, 7L);
        verifyNoInteractions(storage);
    }

    @Test
    void authorizationFailurePreventsOriginalReads() {
        when(access.requireView(3L, 7L)).thenThrow(new BusinessException(
            com.antshorttv.common.ErrorCode.FORBIDDEN, "Denied"
        ));
        assertThatThrownBy(() -> service.delivery(7L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage, renditions, grants);
    }

    @Test
    void historicalPublicStyleIsCompressedInsteadOfServedDirectly() {
        project.coverUrl = "/api/style-library/images/legacy-style";
        var style = new StyleLibraryEntity();
        style.setIsPublic(true);
        style.setStoragePath("style-library/public/legacy.png");
        when(styles.selectOne(any())).thenReturn(style);
        when(storage.resource(style.getStoragePath())).thenReturn(new ByteArrayResource(png));
        assertThat(service.delivery(7L)).isNull();
        verify(renditions).registerOriginalAndSubmit(any(), any(), eq(12), eq(8), anyString());
        verifyNoInteractions(grants);
    }

    @Test
    void unknownExternalUrlNeverFallsBackToOriginal() {
        project.coverUrl = "https://external.example/original.png";
        assertThat(service.status(7L).status()).isEqualTo("FAILED");
        verifyNoInteractions(storage, grants);
    }

    @Test
    void sourceVersionChangesWhenCoverChangesAndNeverUsesSignedQuery() {
        String oldVersion = ProjectCoverImageService.version(project);
        project.coverUrl = "data:image/png;base64,changed";
        assertThat(ProjectCoverImageService.version(project)).isNotEqualTo(oldVersion);
    }

    @Test
    void concurrentClaimLoserDoesNotUploadOrSubmit() {
        when(projects.claimCover(any(), anyString(), anyString(), anyBoolean())).thenReturn(0);
        assertThat(service.delivery(7L)).isNull();
        verifyNoInteractions(storage, grants);
        verify(renditions, never()).registerOriginalAndSubmit(any(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void publicReadyStyleReusesItsCompressedObjectWithoutUploading() {
        project.coverUrl = "/api/style-library/images/new-style";
        var style = new StyleLibraryEntity();
        style.setId(22L);
        style.setExternalId("new-style");
        style.setIsPublic(true);
        style.setStoragePath("materials/0/style_library/202610/22/new-style/derived/display.png");
        when(styles.selectOne(any())).thenReturn(style);
        when(renditions.displayDetails(new MediaObjectIdentity(0L, null, "STYLE_LIBRARY", 22L, "new-style")))
            .thenReturn(new RegisteredMediaDetails(3L,
                new MediaObjectIdentity(0L, null, "STYLE_LIBRARY", 22L, "new-style"),
                "DISPLAY_IMAGE_SLIM", style.getStoragePath(), "image/png", 100, "READY", null));
        assertThat(service.delivery(7L)).isNotNull();
        verifyNoInteractions(storage);
        verify(renditions, never()).registerOriginalAndSubmit(any(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void retryUsesTheValidatedReusedSourceIdentityWhenItsDisplayFailed() {
        project.coverUrl = "/api/style-library/images/retry-style";
        var style = new StyleLibraryEntity();
        style.setId(24L);
        style.setExternalId("retry-style");
        style.setIsPublic(true);
        when(styles.selectOne(any())).thenReturn(style);
        var sourceIdentity = new MediaObjectIdentity(0L, null, "STYLE_LIBRARY", 24L, "retry-style");
        when(renditions.displayDetails(sourceIdentity)).thenReturn(new RegisteredMediaDetails(
            4L, sourceIdentity, "DISPLAY_IMAGE_SLIM", "style/derived/display.png", "image/png", 0,
            "FAILED", "provider failure"));
        when(access.requireView(3L, 7L)).thenReturn(new ProjectAccessContext(
            new TenantContext(9L, 3L, 1L, "OWNER"), project, ProjectAccessSource.TENANT_WIDE,
            null, null, Set.of("PROJECT:VIEW", "PROJECT:EDIT"),
            new ProjectCapabilities(true, true, false, false, false)));

        assertThat(service.retry(7L).status()).isEqualTo("PENDING");

        verify(renditions).retryFailedDisplay(sourceIdentity, "project-cover-7-" + ProjectCoverImageService.version(project));
        verify(renditions, never()).registerOriginalAndSubmit(any(), any(), anyInt(), anyInt(), anyString());
        verifyNoInteractions(storage);
    }

    @Test
    void foreignProjectMaterialIsRetainedAsFailedWithoutFetching() {
        project.coverUrl = "/materials/3/99/ai_image/202610/1/v1/original.png";
        assertThat(service.status(7L).status()).isEqualTo("FAILED");
        verifyNoInteractions(storage, grants);
    }

    @Test
    void repeatedCompletedUploadUsesOneOriginalAndOneProcessingSubmission() {
        when(projects.claimCover(any(), anyString(), anyString(), anyBoolean())).thenReturn(1, 0);
        String source = "materials/3/7/uploads/202610/session/v1/original.png";
        when(storage.resource(source)).thenReturn(new ByteArrayResource(png));
        when(storage.copyCompletedUploadOriginal(any(), anyString())).thenAnswer(call ->
            new StoredObject(call.getArgument(1), png.length, "image/png", "etag", "INTELLIGENT_TIERING"));
        var upload = new VerifiedMediaUpload("session", source, "image/png", png.length, "etag");
        assertThat(service.bindUpload(project, upload).status()).isEqualTo("PENDING");
        service.bindUpload(project, upload);
        verify(storage, times(1)).copyCompletedUploadOriginal(any(), anyString());
        verify(renditions, times(1)).registerOriginalAndSubmit(any(), any(), eq(12), eq(8), anyString());
        assertThat(project.coverUrl).startsWith("materials/3/7/project_cover/");
        assertThat(project.coverUrl).doesNotContain("data:");
    }

    @Test
    void newUnsupportedSourceHasDurableFailureDiagnosticWithoutExternalFetch() {
        service.assign(project, "https://external.example/source.png");
        assertThat(project.coverStatus).isEqualTo("FAILED");
        assertThat(project.coverError).isNotBlank();
        assertThat(project.coverUrl).isEqualTo("https://external.example/source.png");
        verifyNoInteractions(storage, grants);
    }

    @Test
    void oversizedInlineCoverIsRejectedBeforeProjectPersistence() {
        assertThatThrownBy(() -> service.assign(project, "data:image/png;base64," + "A".repeat(501)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("controlled media session");
    }

    @Test
    void malformedHistoricalUrlIsRetainedAsFailedInsteadOfAbortingProjectEdit() {
        service.assign(project, "https://%invalid/source.png");
        assertThat(project.coverStatus).isEqualTo("FAILED");
        assertThat(project.coverUrl).isEqualTo("https://%invalid/source.png");
    }

    @Test
    void retiredPlatformObjectCannotBeRevivedByCoverConversion() {
        project.coverUrl = "/materials/3/7/ai_image/202610/8/result-8/derived/display.png";
        ProjectCoverObject retired = new ProjectCoverObject();
        retired.tenantId = 3L;
        retired.projectId = 7L;
        retired.assetType = "AI_IMAGE_RESULT";
        retired.assetId = 8L;
        retired.versionId = "result-8";
        retired.renditionType = "DISPLAY_IMAGE_SLIM";
        retired.status = "RETIRED";
        when(projects.selectOwnedCoverObject(3L, 7L, project.coverUrl.substring(1))).thenReturn(retired);
        assertThat(service.status(7L).status()).isEqualTo("FAILED");
        verifyNoInteractions(storage, grants);
    }

    @Test
    void archivedProjectCannotReplaceCoverThroughUploadBinding() {
        project.status = "ARCHIVED";
        var upload = new VerifiedMediaUpload("session",
            "materials/3/7/uploads/202610/session/v1/original.png", "image/png", png.length, "etag");
        assertThatThrownBy(() -> service.bindUpload(project, upload)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage, renditions, grants);
    }

    @Test
    void coverSourceIsPreservedWhenEditingReturnedEndpoint() {
        assertThat(ProjectCoverImageService.source(project, ProjectCoverImageService.url(project)))
            .isEqualTo(project.coverUrl);
        assertThat(ProjectCoverImageService.source(project, null)).isNull();
        assertThat(ProjectCoverImageService.source(project, "data:new")).isEqualTo("data:new");
        project.coverUrl = null;
        assertThat(ProjectCoverImageService.url(project)).isNull();
    }
}
