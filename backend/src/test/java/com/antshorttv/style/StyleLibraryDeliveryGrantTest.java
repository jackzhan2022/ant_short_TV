package com.antshorttv.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.auth.AuthService;
import com.antshorttv.auth.RegisterRequest;
import com.antshorttv.auth.VerificationCodeService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.MediaObjectRegistry;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.StoredObject;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "inspiration.image-rendition.scheduler.fixed-delay-ms=3600000")
@AutoConfigureMockMvc
class StyleLibraryDeliveryGrantTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private StyleLibraryMapper mapper;
    @Autowired private StyleLibraryImageStorage storage;
    @Autowired private MediaObjectRegistry registry;
    @Autowired private ObjectStorageKeyFactory keys;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService auth;
    @MockBean private VerificationCodeService verificationCodes;
    private StyleLibraryEntity style;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from media_delivery_grant");
        jdbc.update("delete from media_processing_job");
        jdbc.update("delete from media_object");
        jdbc.update("delete from style_library where external_id = 'grant-style'");
        style = new StyleLibraryEntity();
        style.setExternalId("grant-style");
        style.setName("Grant style");
        style.setCategory("Test");
        style.setDescription("");
        style.setSourceImageUrl("");
        style.setStoragePath("");
        style.setImageUrl("/api/style-library/images/grant-style");
        style.setImageWidth(12);
        style.setImageHeight(8);
        style.setIsPublic(true);
        style.setSortOrder(140);
        style.setCreatedAt(LocalDateTime.now());
        style.setUpdatedAt(LocalDateTime.now());
        mapper.insert(style);
        String original = keys.tenantOriginal(0L, "style_library", style.getId(), style.getExternalId(), LocalDate.now(), "png");
        MediaObjectIdentity identity = new MediaObjectIdentity(0L, null, "STYLE_LIBRARY", style.getId(), style.getExternalId());
        registry.registerOriginal(identity, new StoredObject(original, 100L, "image/png", "original-etag", "INTELLIGENT_TIERING"), null, 12, 8);
        style.setStoragePath(keys.rendition(original, "display", "png"));
        mapper.updateById(style);
        registry.registerPendingRendition(identity, "DISPLAY_IMAGE_SLIM", style.getStoragePath(), "image/png", "INTELLIGENT_TIERING");
        jdbc.update("update media_object set status = 'READY', file_size = 55, width = 12, height = 8, etag = 'display-etag' where rendition_type = 'DISPLAY_IMAGE_SLIM'");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicStyleRedirectsReusePersistentGrantPeriodAcrossRequests(boolean authenticated) throws Exception {
        Cookie cookie = null;
        long subjectId = 0L;
        if (authenticated) {
            var session = auth.register(new RegisterRequest("139%08d".formatted(System.nanoTime() % 100000000),
                "123456", "Style user", "Password123"), null);
            cookie = new Cookie("ANT_SHORT_SESSION", session.issuedSession().credential());
            subjectId = session.response().user().id();
        }
        assertStableGrant(style, cookie, subjectId);
    }

    @Test
    void historicalPublicStyleRetainsItsPathAndStableAnonymousGrant() throws Exception {
        StyleLibraryEntity historical = mapper.selectById(1L);
        String path = historical.getStoragePath();

        assertStableGrant(historical, null, 0L);

        assertThat(mapper.selectById(historical.getId()).getStoragePath()).isEqualTo(path);
        assertThat(jdbc.queryForObject("select count(*) from media_object where asset_id = ?", Integer.class, historical.getId())).isZero();
    }

    @Test
    void nonpublicStyleNeverReceivesADeliveryGrant() throws Exception {
        style.setIsPublic(false);
        mapper.updateById(style);

        mockMvc.perform(get("/api/style-library/images/{externalId}", style.getExternalId()))
            .andExpect(status().isNotFound());
        assertThatThrownBy(() -> storage.deliveryUrl(style)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertNoGrants();
    }

    @Test
    void missingStyleNeverReceivesADeliveryGrant() throws Exception {
        mockMvc.perform(get("/api/style-library/images/missing-style")).andExpect(status().isNotFound());
        assertNoGrants();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED"})
    void unavailableDisplayNeverReceivesADeliveryGrant(String state) throws Exception {
        jdbc.update("update media_object set status = ? where rendition_type = 'DISPLAY_IMAGE_SLIM'", state);

        mockMvc.perform(get("/api/style-library/images/{externalId}", style.getExternalId()))
            .andExpect(status().isNotFound());
        assertNoGrants();
    }

    private void assertStableGrant(StyleLibraryEntity target, Cookie cookie, long subjectId) throws Exception {
        String first = redirect(target, cookie);
        Thread.sleep(1100);
        String second = redirect(target, cookie);

        assertThat(second).isEqualTo(first);
        assertThat(first).startsWith("https://antvcdn.aixmax.cn/" + target.getStoragePath() + "?");
        assertThat(first).contains("t=", "sign=");
        assertThat(jdbc.queryForObject("select count(*) from media_delivery_grant", Integer.class)).isEqualTo(1);
        Map<String, Object> grant = jdbc.queryForMap("select * from media_delivery_grant");
        assertThat(((Number) grant.get("tenant_id")).longValue()).isZero();
        assertThat(grant.get("project_id")).isNull();
        assertThat(((Number) grant.get("user_id")).longValue()).isEqualTo(subjectId);
        assertThat(grant.get("resource_type")).isEqualTo("STYLE_LIBRARY");
        assertThat(((Number) grant.get("resource_id")).longValue()).isEqualTo(target.getId());
        assertThat(grant.get("version_id")).isEqualTo(target.getExternalId());
        assertThat(grant.get("rendition_type")).isEqualTo("DISPLAY");
        assertThat(((Number) grant.get("revision")).intValue()).isEqualTo(1);
    }

    private String redirect(StyleLibraryEntity target, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/style-library/images/{externalId}", target.getExternalId());
        if (cookie != null) request.cookie(cookie);
        return mockMvc.perform(request).andExpect(status().isFound()).andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private void assertNoGrants() {
        assertThat(jdbc.queryForObject("select count(*) from media_delivery_grant", Integer.class)).isZero();
    }
}
