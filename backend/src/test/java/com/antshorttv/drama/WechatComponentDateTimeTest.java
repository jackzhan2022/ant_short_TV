package com.antshorttv.drama;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class WechatComponentDateTimeTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final WechatComponentClient client = mock(WechatComponentClient.class);
    private final Map<String, Object> component = new HashMap<>();
    private WechatComponentService service;

    @BeforeEach
    void setUp() throws Exception {
        var env = new MockEnvironment()
            .withProperty("WECHAT_COMPONENT_ENABLED", "true")
            .withProperty("WECHAT_COMPONENT_APPID", WechatComponentCryptoTest.APP)
            .withProperty("WECHAT_COMPONENT_SECRET", "fixture-secret")
            .withProperty("WECHAT_COMPONENT_EVENT_TOKEN", "event-token")
            .withProperty("WECHAT_COMPONENT_ENCODING_AES_KEY", WechatComponentCryptoTest.MASTER.substring(0, 43))
            .withProperty("WECHAT_COMPONENT_MASTER_KEY", WechatComponentCryptoTest.MASTER)
            .withProperty("WECHAT_COMPONENT_CALLBACK_URL", "https://example.test/api/wechat/component/callback");
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new WechatComponentService(env, db, client, manager);
        component.put("ticket_cipher", WechatComponentCrypto.seal("ticket", WechatComponentCryptoTest.MASTER, "ticket"));
        component.put("token_cipher", WechatComponentCrypto.seal("cached-component", WechatComponentCryptoTest.MASTER, "component-token"));
        when(db.queryForMap("select * from wechat_component_state where id=1")).thenReturn(component);
        when(db.queryForMap("select * from wechat_component_state where id=1 for update")).thenReturn(component);
        var json = new ObjectMapper();
        when(client.post(eq("api_component_token"), isNull(), anyMap()))
            .thenReturn(json.readTree("{\"component_access_token\":\"refreshed-component\",\"expires_in\":7200}"));
        when(client.post(eq("api_create_preauthcode"), anyString(), anyMap()))
            .thenReturn(json.readTree("{\"pre_auth_code\":\"first-code\",\"expires_in\":600}"))
            .thenReturn(json.readTree("{\"pre_auth_code\":\"second-code\",\"expires_in\":600}"));
    }

    private Object jdbcTime(LocalDateTime value, boolean mysql) {
        return mysql ? value : Timestamp.valueOf(value);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatedAuthorizationRequestsReuseCachedTokenForBothJdbcTimeTypes(boolean mysql) {
        component.put("token_expires_at", jdbcTime(LocalDateTime.now().plusHours(1), mysql));
        var first = assertDoesNotThrow(() -> service.create(1L, 1L));
        var second = assertDoesNotThrow(() -> service.create(1L, 1L));
        assertNotEquals(first.requestId(), second.requestId());
        assertTrue(first.authorizationUrl().contains("pre_auth_code=first-code"));
        assertTrue(second.authorizationUrl().contains("pre_auth_code=second-code"));
        verify(client, never()).post(eq("api_component_token"), any(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void expiredComponentTokenRefreshesForBothJdbcTimeTypes(boolean mysql) {
        component.put("token_expires_at", jdbcTime(LocalDateTime.now().minusSeconds(1), mysql));
        assertEquals("refreshed-component", assertDoesNotThrow(service::componentToken));
        verify(client).post(eq("api_component_token"), isNull(), anyMap());
    }

    @Test
    void missingCachedTokenExpiryStillFetchesFirstToken() {
        component.put("token_expires_at", null);
        assertEquals("refreshed-component", service.componentToken());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void authorizationStatusPreservesExpiryForBothJdbcTimeTypes(boolean mysql) {
        var expires = LocalDateTime.now().plusMinutes(5).withNano(0);
        var row = new HashMap<String, Object>(Map.of("id", "request", "status", "PENDING", "expires_at", jdbcTime(expires, mysql)));
        when(db.queryForList("select id,status,appid,expires_at from wechat_authorization_request where id=? and tenant_id=?", "request", 1L))
            .thenReturn(List.of(row));
        var result = assertDoesNotThrow(() -> service.request(1L, "request"));
        assertEquals(expires, result.expiresAt());
        assertEquals("PENDING", result.status());
        row.put("expires_at", jdbcTime(LocalDateTime.now().minusSeconds(1), mysql));
        assertEquals("EXPIRED", service.request(1L, "request").status());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cachedAuthorizerTokenSupportsBothJdbcTimeTypes(boolean mysql) {
        component.put("token_expires_at", jdbcTime(LocalDateTime.now().plusHours(1), mysql));
        String appid = WechatComponentCryptoTest.APP;
        when(db.queryForMap("select * from wechat_authorizer where appid=? for update", appid)).thenReturn(Map.of(
            "auth_status", "AUTHORIZED", "token_expires_at", jdbcTime(LocalDateTime.now().plusHours(1), mysql),
            "access_cipher", WechatComponentCrypto.seal("cached-authorizer", WechatComponentCryptoTest.MASTER, appid + ":access")));
        assertEquals("cached-authorizer", assertDoesNotThrow(() -> service.authorizerToken(appid)));
        verifyNoInteractions(client);
    }
}
