package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.ByteArrayInputStream;

class VideoFrameCaptureControllerTest {
    VideoFrameCaptureService service=mock(VideoFrameCaptureService.class);
    ObjectStorageService storage=mock(ObjectStorageService.class);
    ProjectPermissionGuard permissions=mock(ProjectPermissionGuard.class);
    MockMvc mvc;
    String path="/api/projects/2/ai-video-results/3/frame-source";
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new VideoFrameCaptureController(service,storage,permissions)).build();
        when(service.sourceTenantId(2L,3L)).thenReturn(1L);
        when(service.source(1L,2L,3L)).thenReturn(new VideoFrameCaptureService.Source("video-key",5d,4L));
        when(storage.metadata("video-key")).thenReturn(new StoredObject("video-key",100,"video/mp4","etag","STANDARD"));
    }
    @Test void streamsRequestedBytesWithRangeHeadersWithoutRedirect() throws Exception {
        when(storage.rangeResource("video-key",20,29)).thenReturn(new InputStreamResource(new ByteArrayInputStream(new byte[10])));
        mvc.perform(get(path).header("X-Tenant-Id","1").header("Range","bytes=20-29"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 20-29/100"))
            .andExpect(header().string("Content-Length","10")).andExpect(content().bytes(new byte[10]));
        verify(permissions).require(1L,2L,"AI_VIDEO_TASK:VIEW");
        verify(storage,never()).resource("video-key");
    }
    @Test void rejectsUnsatisfiableRangeWithoutFetchingObject() throws Exception {
        mvc.perform(get(path).header("X-Tenant-Id","1").header("Range","bytes=100-200"))
            .andExpect(status().isRequestedRangeNotSatisfiable()).andExpect(header().string("Content-Range","bytes */100"));
        verify(storage,never()).rangeResource(anyString(),anyLong(),anyLong());
    }
    @Test void saveRequiresAllThreePermissionsAndReturnsContract() throws Exception {
        when(permissions.require(1L,2L,"STORYBOARD:EDIT")).thenReturn(new TenantContext(9L,1L,8L,"MEMBER"));
        when(service.save(1L,2L,3L,9L,"token",1.25)).thenReturn(new VideoFrameCaptureService.FrameResponse(7L,"image","thumb",1.25,3L));
        mvc.perform(post("/api/projects/2/ai-video-results/3/frames").header("X-Tenant-Id","1")
            .contentType("application/json").content("{\"sessionToken\":\"token\",\"timeSeconds\":1.25}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7))
            .andExpect(jsonPath("$.data.sourceVideoResultId").value(3));
        verify(permissions).require(1L,2L,"STORYBOARD:EDIT");
        verify(permissions).require(1L,2L,"AI_VIDEO_TASK:VIEW");
        verify(permissions).require(1L,2L,"AI_IMAGE_TASK:VIEW");
    }
    @Test void streamsWholeVideoAndSuffixRange() throws Exception {
        when(storage.resource("video-key")).thenReturn(new InputStreamResource(new ByteArrayInputStream(new byte[100])));
        mvc.perform(get(path).header("X-Tenant-Id","1")).andExpect(status().isOk())
            .andExpect(header().string("Accept-Ranges","bytes")).andExpect(content().bytes(new byte[100]));
        when(storage.rangeResource("video-key",90,99)).thenReturn(new InputStreamResource(new ByteArrayInputStream(new byte[10])));
        mvc.perform(get(path).header("X-Tenant-Id","1").header("Range","bytes=-10"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 90-99/100"));
    }
    @Test void rejectsMissingTimestampBeforeSaving() throws Exception {
        mvc.perform(post("/api/projects/2/ai-video-results/3/frames").header("X-Tenant-Id","1")
            .contentType("application/json").content("{\"sessionToken\":\"token\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void nativeVideoCanReadWithoutCustomTenantHeader() throws Exception {
        when(storage.resource("video-key")).thenReturn(new InputStreamResource(new ByteArrayInputStream(new byte[100])));
        mvc.perform(get(path).cookie(new jakarta.servlet.http.Cookie("session", "authenticated-session")))
            .andExpect(status().isOk()).andExpect(content().bytes(new byte[100]));
        verify(permissions).require(1L,2L,"AI_VIDEO_TASK:VIEW");
    }
}
