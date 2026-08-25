package com.antshorttv.inspiration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.auth.RegisterRequest;
import com.antshorttv.auth.AuthService;
import com.antshorttv.storage.ObjectStorageService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InspirationCreationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InspirationCreationMapper mapper;

    @Autowired
    private AuthService authService;

    @MockBean
    private ObjectStorageService objectStorageService;

    private Cookie sessionCookie;

    @BeforeEach
    void setUp() {
        mapper.delete(null);
        String credential = authService.register(new RegisterRequest(
            "139%08d".formatted(System.nanoTime() % 100000000),
            "123456",
            "接口测试用户",
            "Password123"
        ), null).issuedSession().credential();
        sessionCookie = new Cookie("ANT_SHORT_SESSION", credential);
    }

    @Test
    void listReturnsOnlyImportedRecordsWithoutDetailJson() throws Exception {
        InspirationCreationEntity first = creation("external-1", "IMPORTED", 10);
        first.setTitle("第一条");
        first.setDetailJson("{\"prompt\":\"hidden\"}");
        mapper.insert(first);
        first.setUrl("/api/inspiration-creations/%d/file".formatted(first.getId()));
        mapper.updateById(first);
        InspirationCreationEntity failed = creation("external-2", "FAILED", 1);
        mapper.insert(failed);

        mockMvc.perform(get("/api/inspiration-creations")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].externalId", is("external-1")))
            .andExpect(jsonPath("$.data[0].title", is("第一条")))
            .andExpect(jsonPath("$.data[0].authorName", is("管理员")))
            .andExpect(jsonPath("$.data[0].url", is("/api/inspiration-creations/%d/file".formatted(first.getId()))))
            .andExpect(jsonPath("$.data[0].detailJson").doesNotExist());
    }

    @Test
    void detailReturnsSanitizedJsonAndMissingRecordsAreHidden() throws Exception {
        InspirationCreationEntity entity = creation("external-3", "IMPORTED", 1);
        entity.setDetailJson("{\"url\":\"/api/inspiration-creations/local/file\",\"prompt\":\"干净详情\"}");
        mapper.insert(entity);

        mockMvc.perform(get("/api/inspiration-creations/{id}", entity.getId())
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalId", is("external-3")))
            .andExpect(jsonPath("$.data.detailJson.prompt", is("干净详情")))
            .andExpect(jsonPath("$.data.detailJson.url", is("/api/inspiration-creations/local/file")))
            .andExpect(jsonPath("$.data.detailJson.url", not("https://external.example/source.png")));

        mockMvc.perform(get("/api/inspiration-creations/{id}", 999999L)
                .cookie(sessionCookie))
            .andExpect(status().isNotFound());
    }

    @Test
    void fileEndpointStreamsImportedMedia() throws Exception {
        InspirationCreationEntity entity = creation("external-4", "IMPORTED", 1);
        entity.setMimeType("video/mp4");
        entity.setStoragePath("inspiration/creations/external-4/original.mp4");
        mapper.insert(entity);
        when(objectStorageService.resource("inspiration/creations/external-4/original.mp4"))
            .thenReturn(new ByteArrayResource("video".getBytes()));

        mockMvc.perform(get("/api/inspiration-creations/{id}/file", entity.getId())
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.valueOf("video/mp4")))
            .andExpect(content().bytes("video".getBytes()));
    }

    @Test
    void endpointsRequireAuthenticatedUser() throws Exception {
        InspirationCreationEntity entity = creation("external-auth", "IMPORTED", 1);
        mapper.insert(entity);

        mockMvc.perform(get("/api/inspiration-creations"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inspiration-creations/{id}", entity.getId()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inspiration-creations/{id}/file", entity.getId()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inspiration-creations/{id}/file", 999999L))
            .andExpect(status().isUnauthorized());
    }

    private InspirationCreationEntity creation(String externalId, String status, int sortOrder) {
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setExternalId(externalId);
        entity.setExternalTaskId("task-" + externalId);
        entity.setCreationType("IMAGE");
        entity.setTaskType("TEXT_TO_IMAGE");
        entity.setTitle("标题-" + externalId);
        entity.setAuthorName("管理员");
        entity.setUrl("/api/inspiration-creations/local/file");
        entity.setStoragePath("inspiration/creations/%s/original.png".formatted(externalId));
        entity.setMimeType("image/png");
        entity.setFileSize(10L);
        entity.setSourceCreatedAt(LocalDateTime.of(2026, 8, 21, 12, 0));
        entity.setImportStatus(status);
        entity.setSortOrder(sortOrder);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
