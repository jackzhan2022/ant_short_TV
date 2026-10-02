package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class MediaUploadControllerTest {

    @Test
    void createsTenantUploadFromAuthenticatedMemberContext() {
        MediaUploadSessionService service = mock(MediaUploadSessionService.class);
        TenantContextResolver tenants = mock(TenantContextResolver.class);
        ProjectPermissionGuard projects = mock(ProjectPermissionGuard.class);
        when(tenants.requireActiveMember(11L)).thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
        MediaUploadSession expected = session();
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(expected);
        MediaUploadController controller = new MediaUploadController(service, tenants, projects);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Tenant-Id", "11");

        var response = controller.create(
            new CreateMediaUploadRequest(null, "episode.mp4", "video/mp4", null), servletRequest
        );

        assertThat(response.data()).isEqualTo(expected);
        ArgumentCaptor<CreateMediaUploadSession> command = ArgumentCaptor.forClass(CreateMediaUploadSession.class);
        verify(service).create(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo(11L);
        assertThat(command.getValue().userId()).isEqualTo(33L);
        assertThat(command.getValue().projectId()).isNull();
    }

    @Test
    void requiresProjectEditBeforeProjectUpload() {
        MediaUploadSessionService service = mock(MediaUploadSessionService.class);
        TenantContextResolver tenants = mock(TenantContextResolver.class);
        ProjectPermissionGuard projects = mock(ProjectPermissionGuard.class);
        when(projects.require(11L, 22L, "PROJECT:EDIT"))
            .thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
        when(service.create(org.mockito.ArgumentMatchers.any())).thenReturn(session());
        MediaUploadController controller = new MediaUploadController(service, tenants, projects);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Tenant-Id", "11");

        controller.create(new CreateMediaUploadRequest(22L, "image.png", "image/png", 10L), servletRequest);

        verify(projects).require(11L, 22L, "PROJECT:EDIT");
        verify(service).create(org.mockito.ArgumentMatchers.argThat(value ->
            value.tenantId().equals(11L) && value.projectId().equals(22L) && value.userId().equals(33L)
        ));
    }

    @Test
    void completeUsesCurrentMemberIdentity() {
        MediaUploadSessionService service = mock(MediaUploadSessionService.class);
        TenantContextResolver tenants = mock(TenantContextResolver.class);
        ProjectPermissionGuard projects = mock(ProjectPermissionGuard.class);
        when(tenants.requireActiveMember(11L)).thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
        when(service.complete(eq(33L), eq("session-1"))).thenReturn(
            new VerifiedMediaUpload("session-1", "uploads/11/session-1/source.mp4", "video/mp4", 10, "etag")
        );
        MediaUploadController controller = new MediaUploadController(service, tenants, projects);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Tenant-Id", "11");

        assertThat(controller.complete("session-1", servletRequest).data().eTag()).isEqualTo("etag");
    }

    @Test
    void authorizesCosRequestUsingCurrentMemberIdentity() {
        MediaUploadSessionService service = mock(MediaUploadSessionService.class);
        TenantContextResolver tenants = mock(TenantContextResolver.class);
        ProjectPermissionGuard projects = mock(ProjectPermissionGuard.class);
        when(tenants.requireActiveMember(11L)).thenReturn(new TenantContext(33L, 11L, 44L, "MEMBER"));
        CosUploadAuthorizationRequest body = new CosUploadAuthorizationRequest(
            "PUT",
            "/uploads/11/session-1/source.mp4",
            Map.of(),
            Map.of("host", "cos.example")
        );
        when(service.authorize(33L, "session-1", body)).thenReturn(
            new CosUploadAuthorization("authorization", "security-token", 1_700_000_300L)
        );
        MediaUploadController controller = new MediaUploadController(service, tenants, projects);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Tenant-Id", "11");

        var value = controller.authorize("session-1", body, servletRequest).data();

        assertThat(value.authorization()).isEqualTo("authorization");
        verify(service).authorize(33L, "session-1", body);
    }

    private MediaUploadSession session() {
        return new MediaUploadSession(
            "session-1", "antv-1418200553", "ap-guangzhou", "INTELLIGENT_TIERING",
            "uploads/11/session-1/source.mp4",
            "PENDING", Instant.parse("2026-10-07T00:00:00Z")
        );
    }
}
