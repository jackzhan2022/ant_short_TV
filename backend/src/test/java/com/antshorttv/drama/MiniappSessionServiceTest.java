package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.drama.DramaModels.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MiniappSessionServiceTest {
    MiniappMapper apps = mock(MiniappMapper.class);
    ViewerSessionMapper sessions = mock(ViewerSessionMapper.class);
    WechatMiniappIdentity identity = mock(WechatMiniappIdentity.class);
    com.antshorttv.tenant.TenantMapper tenants = mock(com.antshorttv.tenant.TenantMapper.class);
    MockEnvironment environment = new MockEnvironment()
        .withProperty("miniapp.apps.wx1234567890abcdef.tenant-id", "9");
    MiniappSessionService service = new MiniappSessionService(apps, sessions, identity, tenants, new MiniappOwnership(environment));

    @org.junit.jupiter.api.BeforeEach void setup() {
        var team = new com.antshorttv.tenant.TenantEntity(); team.setStatus("ACTIVE");
        when(tenants.selectById(any())).thenReturn(team);
    }

    @Test void missingServerSecretFailsWithoutNetwork() {
        var real = new WechatMiniappIdentity(new MockEnvironment(), new ObjectMapper());
        BusinessException error = assertThrows(BusinessException.class,
            () -> real.exchange("wx1234567890abcdef", "code"));
        assertTrue(error.getMessage().contains("未配置"));
    }

    @Test void appidAndBackendCookieAreNotCredentials() {
        for (String header : new String[]{null, "wx1234567890abcdef", "Bearer wx1234567890abcdef", "Cookie backend-session"}) {
            assertThrows(BusinessException.class, () -> service.authenticate(header));
        }
        verifyNoInteractions(apps, sessions, identity);
    }

    @Test void loginStoresOnlyHashedTokenAndBindsApplication() {
        Miniapp app = new Miniapp(); app.id = 5L; app.tenantId = 9L; app.appid = "wx1234567890abcdef";
        when(apps.selectOne(any(Wrapper.class))).thenReturn(app);
        when(identity.exchange(app.appid, "code")).thenReturn("real-openid");
        LoginResponse result = service.login(new LoginRequest(app.appid, "code"));
        assertEquals(43, result.accessToken().length());
        var capture = org.mockito.ArgumentCaptor.forClass(ViewerSession.class);
        verify(sessions).insert(capture.capture());
        assertEquals(5L, capture.getValue().miniappId);
        assertNotEquals(result.accessToken(), capture.getValue().tokenHash);
        assertEquals(MiniappSessionService.hash(result.accessToken()), capture.getValue().tokenHash);
        assertTrue(result.expiresAt().isAfter(LocalDateTime.now().plusMinutes(119)));
    }

    @Test void expiredOrUnknownSessionRejected() {
        assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + "a".repeat(43)));
        verifyNoInteractions(apps);
    }

    @Test void disabledAppInvalidatesExistingSession() {
        ViewerSession session = new ViewerSession(); session.miniappId = 5L;
        when(sessions.selectOne(any(Wrapper.class))).thenReturn(session);
        assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + "a".repeat(43)));
    }

    @Test void disabledTeamInvalidatesExistingSession() {
        ViewerSession session = new ViewerSession(); session.miniappId = 5L;
        when(sessions.selectOne(any(Wrapper.class))).thenReturn(session);
        Miniapp app = new Miniapp(); app.id = 5L; app.tenantId = 9L; app.appid = "wx1234567890abcdef";
        when(apps.selectOne(any(Wrapper.class))).thenReturn(app);
        var team = new com.antshorttv.tenant.TenantEntity(); team.setStatus("DISABLED");
        when(tenants.selectById(9L)).thenReturn(team);
        assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + "a".repeat(43)));
    }

    @Test void secretWithoutTrustedOwnershipCannotLoginOrAuthenticate() {
        Miniapp app = new Miniapp(); app.id = 5L; app.tenantId = 9L; app.appid = "wx1234567890abcdef";
        when(apps.selectOne(any(Wrapper.class))).thenReturn(app);
        environment.setProperty("miniapp.apps." + app.appid + ".secret", "test-secret-never-returned");
        environment.setProperty("miniapp.apps." + app.appid + ".tenant-id", "");
        BusinessException error = assertThrows(BusinessException.class,
            () -> service.login(new LoginRequest(app.appid, "code")));
        assertTrue(error.getMessage().contains("未配置"));
        assertFalse(error.getMessage().contains("test-secret"));
        ViewerSession session = new ViewerSession(); session.miniappId = 5L;
        when(sessions.selectOne(any(Wrapper.class))).thenReturn(session);
        assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + "a".repeat(43)));
        verifyNoInteractions(identity);
        verify(sessions, never()).insert(any(ViewerSession.class));
    }

    @Test void ownershipChangeInvalidatesSessionAndBlocksLogin() {
        Miniapp app = new Miniapp(); app.id = 5L; app.tenantId = 9L; app.appid = "wx1234567890abcdef";
        when(apps.selectOne(any(Wrapper.class))).thenReturn(app);
        ViewerSession session = new ViewerSession(); session.miniappId = 5L;
        when(sessions.selectOne(any(Wrapper.class))).thenReturn(session);
        assertEquals(app, service.authenticate("Bearer " + "a".repeat(43)));
        environment.setProperty("miniapp.apps." + app.appid + ".tenant-id", "10");
        assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + "a".repeat(43)));
        assertThrows(BusinessException.class, () -> service.login(new LoginRequest(app.appid, "code")));
        environment.setProperty("miniapp.apps." + app.appid + ".tenant-id", "invalid");
        assertThrows(BusinessException.class, () -> service.authenticate("Bearer " + "a".repeat(43)));
        verifyNoInteractions(identity);
    }

    @Test void upstreamFailureDoesNotIssueToken() {
        Miniapp app = new Miniapp(); app.id = 5L; app.tenantId = 9L; app.appid = "wx1234567890abcdef";
        when(apps.selectOne(any(Wrapper.class))).thenReturn(app);
        when(identity.exchange(anyString(), anyString())).thenThrow(WechatMiniappIdentity.unauthorized());
        assertThrows(BusinessException.class, () -> service.login(new LoginRequest(app.appid, "bad-code")));
        verifyNoInteractions(sessions);
    }
}
