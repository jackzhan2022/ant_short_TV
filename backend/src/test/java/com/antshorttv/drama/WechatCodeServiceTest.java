package com.antshorttv.drama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.rbac.TenantPermissionGuard;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatCodeServiceTest {
    final ObjectMapper json = new ObjectMapper();
    MiniappMapper apps = mock(MiniappMapper.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    WechatComponentService component = mock(WechatComponentService.class);
    WechatComponentClient client = mock(WechatComponentClient.class);
    PlatformPermissionGuard platform = mock(PlatformPermissionGuard.class);
    TenantPermissionGuard permissions = mock(TenantPermissionGuard.class);
    WechatCodeService service = new WechatCodeService(apps, db, component, client, platform, permissions, json);
    DramaModels.Miniapp app;
    final WechatCodeModels.CommitRequest request = new WechatCodeModels.CommitRequest(0L, "1.0", "测试上传");
    @BeforeEach void setup() throws Exception {
        app = new DramaModels.Miniapp(); app.id = 1L; app.tenantId = 2L; app.appid = "wx1234567890abcdef";
        app.bindingType = "OFFICIAL"; app.authStatus = "AUTHORIZED"; app.enabled = true;
        when(apps.selectById(1L)).thenReturn(app);
        scope(18);
        when(component.componentToken()).thenReturn("component-fixture");
        when(component.authorizerToken(app.appid)).thenReturn("authorizer-fixture");
        when(client.getTemplates(anyString())).thenReturn(json.readTree("{\"errcode\":0,\"template_list\":[{\"template_id\":0,\"template_type\":0,\"user_version\":\"1.0\",\"user_desc\":\"描述\",\"secret\":\"hidden\"}]}"));
        when(client.commitCode(anyString(), anyMap())).thenReturn(json.readTree("{\"errcode\":0}"));
    }
    void scope(int scope) {
        when(db.queryForList(anyString(), eq(app.appid), eq(2L))).thenReturn(List.of(Map.of("scopes", "[{\"funcscope_category\":{\"id\":" + scope + "}}]")));
    }
    @Test void templateZeroAndServerControlledExtAndWhitelist() throws Exception {
        var templates = service.templates(2L);
        assertEquals(0, templates.get(0).templateId()); assertFalse(json.writeValueAsString(templates).contains("hidden"));
        assertEquals("SUCCEEDED", service.commit(2L, 1L, request).status());
        verify(client, times(1)).commitCode(eq("authorizer-fixture"), argThat(body -> {
            assertEquals(0L, body.get("template_id"));
            assertEquals("{\"extEnable\":true,\"extAppid\":\"wx1234567890abcdef\"}", body.get("ext_json"));
            return true;
        }));
    }
    @Test void nonexistentAndStandardTemplateCannotCommit() throws Exception {
        for (String list : List.of("[]", "[{\"template_id\":0,\"template_type\":1}]", "[{\"template_id\":0}]")) {
            when(client.getTemplates(anyString())).thenReturn(json.readTree("{\"errcode\":0,\"template_list\":" + list + "}"));
            assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        }
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void absentTemplateIdIsNotTemplateZero() throws Exception {
        when(client.getTemplates(anyString())).thenReturn(json.readTree("{\"errcode\":0,\"template_list\":[{\"template_type\":0}]}"));
        assertThrows(WechatCodeModels.CodeException.class, () -> service.commit(2L, 1L, request));
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void crossTenantManualDisabledRevokedAndMissingScopeRejected() {
        assertThrows(RuntimeException.class, () -> service.commit(3L, 1L, request));
        app.bindingType = "MANUAL"; assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        app.bindingType = "OFFICIAL"; app.enabled = false; assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        app.enabled = true; app.authStatus = "REVOKED"; assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        app.authStatus = "AUTHORIZED"; scope(17); assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        scope(86); assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        service.trialQrCode(2L, 1L, null);
        verify(client).trialQrCode("authorizer-fixture", null);
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void missingErrcodeAndTimeoutAreUnknownAndNeverRetried() throws Exception {
        when(client.commitCode(anyString(), anyMap())).thenReturn(json.readTree("{}"));
        assertEquals("WECHAT_COMMIT_UNKNOWN", assertThrows(WechatCodeModels.CodeException.class, () -> service.commit(2L, 1L, request)).code());
        verify(client, times(1)).commitCode(anyString(), anyMap());
        clearInvocations(client);
        when(client.commitCode(anyString(), anyMap())).thenThrow(new RuntimeException("access_token=authorizer-fixture"));
        var failure = assertThrows(WechatCodeModels.CodeException.class, () -> service.commit(2L, 1L, request));
        assertEquals("WECHAT_COMMIT_UNKNOWN", failure.code()); assertFalse(failure.toString().contains("authorizer-fixture"));
        verify(client, times(1)).commitCode(anyString(), anyMap());
    }
    @Test void rejectedUploadIsNotSuccessOrRetry() throws Exception {
        when(client.commitCode(anyString(), anyMap())).thenReturn(json.readTree("{\"errcode\":85014,\"errmsg\":\"secret-token\"}"));
        var failure = assertThrows(WechatCodeModels.CodeException.class, () -> service.commit(2L, 1L, request));
        assertEquals("WECHAT_CODE_REJECTED", failure.code()); assertFalse(failure.toString().contains("secret-token"));
        verify(client, times(1)).commitCode(anyString(), anyMap());
    }
    @Test void authorizationRevokedDuringTokenLookupPreventsUpload() {
        when(component.authorizerToken(app.appid)).thenAnswer(i -> { app.authStatus = "REVOKED"; return "authorizer-fixture"; });
        assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void invalidScopePayloadFailsClosed() {
        for (String scopes : List.of("{}", "null", "invalid", "[{\"funcscope_category\":{\"id\":\"18\"}}]")) {
            when(db.queryForList(anyString(), eq(app.appid), eq(2L))).thenReturn(List.of(Map.of("scopes", scopes)));
            assertThrows(RuntimeException.class, () -> service.commit(2L, 1L, request));
        }
        verifyNoInteractions(client);
    }
    @Test void sameAppConcurrentCommitRejectedAndLockReleased() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.commitCode(anyString(), anyMap())).thenAnswer(i -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return json.readTree("{\"errcode\":0}");
        });
        var pool = Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> service.commit(2L, 1L, request));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals("WECHAT_COMMIT_IN_PROGRESS", assertThrows(WechatCodeModels.CodeException.class, () -> service.commit(2L, 1L, request)).code());
            release.countDown(); assertEquals("SUCCEEDED", first.get(5, TimeUnit.SECONDS).status());
            verify(client, times(1)).commitCode(anyString(), anyMap());
            assertEquals("SUCCEEDED", service.commit(2L, 1L, request).status());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
