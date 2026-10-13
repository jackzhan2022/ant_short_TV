package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DramaPersistenceIntegrationTest {
    @Autowired DramaLibraryService library;
    @Autowired LibraryEpisodeMapper episodes;
    @Autowired MiniappMapper apps;
    @Autowired DramaMapper dramas;
    @Autowired MiniappSessionService sessions;
    @Autowired TenantMapper tenants;
    @Autowired MockMvc mvc;
    @Autowired DramaSourceProtection protection;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.core.env.ConfigurableEnvironment environment;
    @MockBean WechatMiniappIdentity identity;
    @MockBean ObjectStorageService storage;

    @Test
    @Transactional
    void migrationAndFixedReleaseIsolationRoundTrip() throws Exception {
        TenantEntity team = new TenantEntity(); team.setCode("drama-test"); team.setName("测试团队");
        team.setType("TEAM"); team.setStatus("ACTIVE"); team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now()); tenants.insert(team);
        Long tenant = team.getId();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("drama-test-ownership",
            java.util.Map.of("miniapp.apps.wx1234567890abcdef.tenant-id", tenant.toString(),
                "miniapp.apps.wxfedcba0987654321.tenant-id", tenant.toString())));
        Drama drama = library.create(tenant, new DramaRequest("原始剧名", "介绍"));
        Miniapp app = library.bind(tenant, new BindingRequest("wx1234567890abcdef", "应用一"));
        Miniapp other = library.bind(tenant, new BindingRequest("wxfedcba0987654321", "应用二"));
        assertEquals("MANUAL", app.bindingType);
        assertEquals(2, library.miniapps(tenant).size());
        Episode episode = new Episode(); episode.tenantId = tenant; episode.dramaId = drama.id;
        episode.episodeNo = 1; episode.title = "第一集"; episode.sourceType = "LOCAL_UPLOAD";
        episode.storagePath = "materials/" + tenant + "/drama/202610/1/v1/original.mp4";
        episode.fileSize = 100L; episode.createdAt = LocalDateTime.now(); episodes.insert(episode);
        when(storage.metadata(episode.storagePath)).thenReturn(new com.antshorttv.storage.StoredObject(
            episode.storagePath, 100, "video/mp4", "etag", "INTELLIGENT_TIERING"));
        library.publish(tenant, app.id, drama.id, new PublishRequest(List.of(episode.id)));
        library.update(tenant, drama.id, new DramaRequest("新剧名", "新介绍"));
        assertEquals("原始剧名", library.catalog(app).get(0).title);
        assertTrue(library.catalog(other).isEmpty());
        assertThrows(com.antshorttv.common.BusinessException.class, () -> library.play(other, drama.id, episode.id));
        assertThrows(com.antshorttv.common.BusinessException.class, () -> library.deleteEpisode(tenant, drama.id, episode.id));
        assertThrows(com.antshorttv.common.BusinessException.class, () -> library.get(tenant + 999, drama.id));
        when(identity.exchange(app.appid, "official-code")).thenReturn("openid-fixture");
        LoginResponse login = sessions.login(new LoginRequest(app.appid, "official-code"));
        when(storage.cdnUrl(eq(episode.storagePath), any())).thenReturn("signed-video-url");
        mvc.perform(get("/api/miniapp/dramas/{id}/episodes/{episodeId}/play", drama.id, episode.id)
            .header("Authorization", "Bearer " + login.accessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("signed-video-url"));
        library.unpublish(tenant, app.id, drama.id);
        assertTrue(library.catalog(app).isEmpty());
        library.deleteEpisode(tenant, drama.id, episode.id);
        sessions.logout("Bearer " + login.accessToken());
        assertThrows(com.antshorttv.common.BusinessException.class, () -> sessions.authenticate("Bearer " + login.accessToken()));
    }

    @Test
    @Transactional
    void publishedObjectAndActiveSourceReferencesBlockDeletion() {
        Drama drama = library.create(1L, new DramaRequest("引用测试", null));
        Episode episode = new Episode(); episode.tenantId = 1L; episode.dramaId = drama.id;
        episode.episodeNo = 1; episode.title = "第一集"; episode.sourceType = "PROJECT_VERSION";
        episode.sourceProjectId = 2L; episode.sourceVersionId = 3L;
        episode.storagePath = "materials/1/2/episode/original.mp4"; episode.fileSize = 100L;
        episode.createdAt = LocalDateTime.now(); episodes.insert(episode);
        assertThrows(com.antshorttv.common.BusinessException.class,
            () -> protection.beforeVersionDelete(1L, 2L, 3L));
        assertThrows(com.antshorttv.common.BusinessException.class,
            () -> protection.beforeObjectDelete(episode.storagePath));
        assertEquals(4, jdbc.queryForObject("select count(*) from permission where code in ('DRAMA:VIEW','DRAMA:MANAGE','DRAMA:PUBLISH','MINIAPP:MANAGE')", Integer.class));
    }

    @Test void playbackRequiresIndependentCredentialsAndAdminRequiresLogin() throws Exception {
        mvc.perform(get("/api/miniapp/dramas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/miniapp/dramas").header("Authorization", "Bearer wx1234567890abcdef"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/drama-library").header("X-Tenant-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/miniapp/auth/login").contentType("application/json")
            .content("{\"appid\":\"invalid\",\"code\":\"x\"}"))
            .andExpect(status().isBadRequest());
    }
}
