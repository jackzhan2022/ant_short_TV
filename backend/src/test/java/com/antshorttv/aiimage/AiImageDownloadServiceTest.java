package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.GlobalExceptionHandler;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiImageDownloadServiceTest {
    private final AiImageResultMapper mapper = mock(AiImageResultMapper.class);
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final MaterialFileAccessService files = mock(MaterialFileAccessService.class);
    private final AiImageDownloadService service = new AiImageDownloadService(mapper, permissions, files, new ObjectStorageKeyFactory());

    @BeforeEach
    void setup() {
        when(mapper.selectById(51L)).thenReturn(image(51L, "png"));
        when(mapper.selectById(52L)).thenReturn(image(52L, "jpeg"));
        when(files.resource("materials/11/22/images/51/original.png")).thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));
        when(files.resource("materials/11/22/images/52/original.jpeg")).thenReturn(new ByteArrayResource(new byte[] {4, 5, 6}));
    }

    @Test
    void singleDownloadReturnsOriginalBytesWithoutCdnRedirect() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AiImageDownloadController(service)).build();
        mvc.perform(get("/api/projects/22/ai-image-results/51/download-file"))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(new byte[] {1, 2, 3}))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        verify(permissions).require(11L, 22L, "AI_IMAGE_TASK:VIEW");
    }

    @Test
    void archivePreservesOriginalContentsAndExtensionsAndDeduplicatesSelections() throws Exception {
        Set<Path> before = archives();
        var download = service.archive(22L, List.of(51L, 52L, 51L));
        assertThat(download.fileName()).endsWith(".zip");
        try (var zip = new ZipInputStream(download.resource().getInputStream(), StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("图片_51.png");
            assertThat(zip.readAllBytes()).containsExactly(1, 2, 3);
            assertThat(zip.getNextEntry().getName()).isEqualTo("图片_52.jpeg");
            assertThat(zip.readAllBytes()).containsExactly(4, 5, 6);
            assertThat(zip.getNextEntry()).isNull();
        }
        assertThat(archives()).isEqualTo(before);
    }

    @Test
    void validatesEverySelectionBeforeReadingAnyObject() {
        var other = image(52L, "jpeg");
        other.setProjectId(23L);
        when(mapper.selectById(52L)).thenReturn(other);
        assertThatThrownBy(() -> service.archive(22L, List.of(51L, 52L))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void rejectsDeletedProcessingMissingAndCrossProjectImages() {
        var result = image(51L, "png");
        when(mapper.selectById(51L)).thenReturn(result);
        for (String status : List.of("DELETED", "PROCESSING")) {
            result.setStatus(status);
            assertThatThrownBy(() -> service.original(22L, 51L)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.original(22L, 99L)).isInstanceOf(BusinessException.class);
        result.setStatus("ACTIVE");
        assertThatThrownBy(() -> service.original(23L, 51L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void checksPermissionsAndStorageOwnership() {
        when(permissions.require(11L, 22L, "AI_IMAGE_TASK:VIEW"))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问"));
        assertThatThrownBy(() -> service.original(22L, 51L)).isInstanceOf(BusinessException.class);
        reset(permissions);
        var result = image(51L, "png");
        when(mapper.selectById(51L)).thenReturn(result);
        for (String key : List.of("materials/12/22/secret.png", "materials/11/23/secret.png", "../secret.png", "https://example.com/image.png", "")) {
            result.setStoragePath(key);
            assertThatThrownBy(() -> service.original(22L, 51L)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(files);
    }

    @Test
    void rejectsEmptyInvalidAndOversizedBatchesBeforeStorageReads() {
        assertThatThrownBy(() -> service.archive(22L, List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.archive(22L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.archive(22L, List.of(0L))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.archive(22L, java.util.Collections.nCopies(101, 51L))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(files);
    }

    @Test
    void removesIncompleteArchivesWhenAnObjectReadFails() throws Exception {
        Set<Path> before = archives();
        when(files.resource("materials/11/22/images/52/original.jpeg")).thenReturn(new AbstractResource() {
            public String getDescription() { return "unreadable original"; }
            public java.io.InputStream getInputStream() throws IOException { throw new IOException("object unavailable"); }
        });
        assertThatThrownBy(() -> service.archive(22L, List.of(51L, 52L))).isInstanceOf(IOException.class);
        assertThat(archives()).isEqualTo(before);
    }

    @Test
    void batchEndpointReturnsZipAttachmentAndValidatesRequest() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AiImageDownloadController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/projects/22/ai-image-results/download-archive")
            .contentType("application/json").content("{\"resultIds\":[51,52]}"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/zip"))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(post("/api/projects/22/ai-image-results/download-archive")
            .contentType("application/json").content("{\"resultIds\":[]}"))
            .andExpect(status().isBadRequest());
        when(permissions.require(11L, 22L, "AI_IMAGE_TASK:VIEW"))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问"));
        mvc.perform(get("/api/projects/22/ai-image-results/51/download-file"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Location"));
    }

    @Test
    void batchDownloadRequiresTheSessionCsrfCookieAndHeader() throws Exception {
        var csrf = new org.springframework.security.web.csrf.CsrfFilter(
            org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse());
        csrf.setRequestHandler(new com.antshorttv.security.SpaCsrfTokenRequestHandler());
        var mvc = MockMvcBuilders.standaloneSetup(new AiImageDownloadController(service)).addFilters(csrf).build();
        mvc.perform(post("/api/projects/22/ai-image-results/download-archive")
            .contentType("application/json").content("{\"resultIds\":[51]}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(files);
        mvc.perform(post("/api/projects/22/ai-image-results/download-archive")
            .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "session-csrf"))
            .header("X-XSRF-TOKEN", "session-csrf")
            .contentType("application/json").content("{\"resultIds\":[51]}"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/zip"));
    }

    private AiImageResultEntity image(long id, String extension) {
        var result = new AiImageResultEntity();
        result.setId(id); result.setTenantId(11L); result.setProjectId(22L); result.setStatus("ACTIVE");
        result.setStoragePath("materials/11/22/images/" + id + "/original." + extension);
        result.setMimeType("image/" + extension); result.setFileSize(3L);
        return result;
    }

    private Set<Path> archives() throws IOException {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(path -> path.getFileName().toString().startsWith("antv-asset-images-"))
                .collect(Collectors.toSet());
        }
    }
}
