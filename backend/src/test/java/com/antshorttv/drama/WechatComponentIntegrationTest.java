package com.antshorttv.drama;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WechatComponentIntegrationTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired WechatComponentService service;
    @Autowired JdbcTemplate db;
    @Autowired ConfigurableEnvironment env;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired MiniappSessionService sessions;
    @Autowired MiniappMapper apps;
    @Autowired DramaLibraryService library;
    @Autowired com.antshorttv.tenant.TenantMapper tenants;
    @MockBean WechatComponentClient client;
    Map<String,Object> config;
    @BeforeEach void setup() throws Exception {
        config = new HashMap<>(Map.of("WECHAT_COMPONENT_ENABLED", "true", "WECHAT_COMPONENT_APPID", WechatComponentCryptoTest.APP,
            "WECHAT_COMPONENT_SECRET", "fixture-secret", "WECHAT_COMPONENT_EVENT_TOKEN", "event-token",
            "WECHAT_COMPONENT_ENCODING_AES_KEY", WechatComponentCryptoTest.MASTER.substring(0,43),
            "WECHAT_COMPONENT_MASTER_KEY", WechatComponentCryptoTest.MASTER,
            "WECHAT_COMPONENT_CALLBACK_URL", "https://example.test/api/wechat/component/callback"));
        env.getPropertySources().addFirst(new MapPropertySource("component-fixture", config));
        db.update("delete from miniapp_viewer_session"); db.update("delete from wechat_authorization_request"); db.update("delete from wechat_authorizer");
        db.update("delete from team_miniapp where binding_type='OFFICIAL'");
        db.update("update wechat_component_state set ticket_cipher=?,ticket_time=1,token_cipher=null,token_expires_at=null where id=1", WechatComponentCrypto.seal("fixture-ticket", WechatComponentCryptoTest.MASTER, "ticket"));
        when(client.post(eq("api_component_token"), isNull(), anyMap())).thenReturn(json.readTree("{\"component_access_token\":\"component-secret\",\"expires_in\":7200}"));
        when(client.post(eq("api_create_preauthcode"), anyString(), anyMap())).thenAnswer(i -> json.readTree("{\"pre_auth_code\":\"" + UUID.randomUUID() + "\",\"expires_in\":600}"));
        when(client.post(eq("api_query_auth"), anyString(), anyMap())).thenReturn(json.readTree("{\"authorization_info\":{\"authorizer_appid\":\"wx1234567890abcdef\",\"authorizer_access_token\":\"access-secret\",\"authorizer_refresh_token\":\"refresh-secret\",\"expires_in\":7200,\"func_info\":[{\"funcscope_category\":{\"id\":17}}]}}"));
        when(client.post(eq("api_get_authorizer_info"), anyString(), anyMap())).thenReturn(json.readTree("{\"authorizer_info\":{\"nick_name\":\"测试应用\",\"MiniProgramInfo\":{}},\"authorization_info\":{\"authorizer_appid\":\"wx1234567890abcdef\"}}"));
        when(client.post(eq("api_authorizer_token"), anyString(), anyMap())).thenReturn(json.readTree("{\"authorizer_access_token\":\"refreshed-secret\",\"authorizer_refresh_token\":\"rotated-secret\",\"expires_in\":7200}"));
    }
    String state(WechatComponentService.Created created) {
        String redirect = java.net.URLDecoder.decode(created.authorizationUrl().split("redirect_uri=")[1].split("&auth_type")[0], java.nio.charset.StandardCharsets.UTF_8);
        return redirect.split("state=")[1];
    }
    @Test void stateIsHashedScopedSingleUseAndExpires() {
        var created = service.create(1L, 1L);
        assertFalse(db.queryForMap("select * from wechat_authorization_request where id=?", created.requestId()).containsValue(state(created)));
        assertThrows(RuntimeException.class, () -> service.request(999L, created.requestId()));
        service.callback(state(created), "code");
        assertEquals("SUCCEEDED", service.request(1L, created.requestId()).status());
        assertThrows(RuntimeException.class, () -> service.callback(state(created), "code"));
        var expired = service.create(1L, 1L);
        db.update("update wechat_authorization_request set expires_at=? where id=?", LocalDateTime.now().minusSeconds(1), expired.requestId());
        assertEquals("EXPIRED", service.request(1L, expired.requestId()).status());
        assertThrows(RuntimeException.class, () -> service.callback(state(expired), "code"));
        var foreign = service.create(2L, 2L);
        assertThrows(RuntimeException.class, () -> service.callback(state(foreign), "code"));
        assertEquals("FAILED", service.request(2L, foreign.requestId()).status());
        assertEquals(1L, db.queryForObject("select tenant_id from wechat_authorizer where appid=?", Long.class, WechatComponentCryptoTest.APP));
    }
    @Test void officialLoginUsesComponentAndRevokeBlocksPublishingAndSigning() throws Exception {
        var team = new com.antshorttv.tenant.TenantEntity(); team.setCode("official-login-team"); team.setName("官方授权团队");
        team.setType("TEAM"); team.setStatus("ACTIVE"); team.setCreatedAt(LocalDateTime.now()); team.setUpdatedAt(LocalDateTime.now()); tenants.insert(team);
        var created = service.create(team.getId(), 1L); service.callback(state(created), "code");
        when(client.login(WechatComponentCryptoTest.APP, "viewer-code", WechatComponentCryptoTest.APP, "component-secret")).thenReturn("official-openid");
        var login = sessions.login(new DramaModels.LoginRequest(WechatComponentCryptoTest.APP, "viewer-code"));
        var app = sessions.authenticate("Bearer " + login.accessToken());
        assertEquals("OFFICIAL", app.bindingType);
        verify(client).login(app.appid, "viewer-code", WechatComponentCryptoTest.APP, "component-secret");
        var drama = library.create(team.getId(), new DramaModels.DramaRequest("撤销测试", null));
        String event = "<xml><AppId>" + app.appid + "</AppId><CreateTime>" + Instant.now().plusSeconds(1).getEpochSecond()
            + "</CreateTime><InfoType>unauthorized</InfoType><AuthorizerAppid>" + app.appid + "</AuthorizerAppid></xml>";
        String encrypted = WechatComponentCryptoTest.encrypt(event, app.appid);
        service.event("<xml><Encrypt>" + encrypted + "</Encrypt></xml>", WechatComponentCryptoTest.signature(encrypted), "1", "nonce");
        assertThrows(RuntimeException.class, () -> sessions.authenticate("Bearer " + login.accessToken()));
        assertThrows(RuntimeException.class, () -> library.enable(team.getId(), app.id, true));
        assertThrows(RuntimeException.class, () -> library.publish(team.getId(), app.id, drama.id, new DramaModels.PublishRequest(List.of(1L))));
        assertThrows(RuntimeException.class, () -> library.play(app, drama.id, 1L));
    }
    @Test void componentRefreshSerializesConcurrentCallers() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> service.componentToken());
            var b = pool.submit(() -> service.componentToken());
            assertEquals("component-secret", a.get()); assertEquals("component-secret", b.get());
            verify(client, times(1)).post(eq("api_component_token"), isNull(), anyMap());
        } finally { pool.shutdownNow(); }
    }
    @Test void updateEventIsIdempotentAndPersistsScopes() throws Exception {
        var created = service.create(1L,1L); service.callback(state(created), "code");
        String event = "<xml><AppId>" + WechatComponentCryptoTest.APP + "</AppId><CreateTime>" + Instant.now().plusSeconds(1).getEpochSecond()
            + "</CreateTime><InfoType>updateauthorized</InfoType><AuthorizerAppid>" + WechatComponentCryptoTest.APP
            + "</AuthorizerAppid><AuthorizationCode>updated-code</AuthorizationCode><AuthorizationCodeExpiredTime>"
            + Instant.now().plusSeconds(600).getEpochSecond() + "</AuthorizationCodeExpiredTime></xml>";
        String encrypted = WechatComponentCryptoTest.encrypt(event, WechatComponentCryptoTest.APP);
        service.event("<xml><Encrypt>" + encrypted + "</Encrypt></xml>", WechatComponentCryptoTest.signature(encrypted), "1", "nonce");
        service.event("<xml><Encrypt>" + encrypted + "</Encrypt></xml>", WechatComponentCryptoTest.signature(encrypted), "1", "nonce");
        verify(client, times(2)).post(eq("api_query_auth"), anyString(), anyMap());
        assertTrue(db.queryForObject("select scopes from wechat_authorizer where appid=?", String.class, WechatComponentCryptoTest.APP).contains("17"));
    }
    @Test void concurrentCallbackConsumesExactlyOnce() throws Exception {
        var created = service.create(1L,1L);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> call = () -> {
                start.await();
                try { service.callback(state(created), "code"); return true; }
                catch (RuntimeException e) { return false; }
            };
            var a = pool.submit(call); var b = pool.submit(call); start.countDown();
            assertNotEquals(a.get(), b.get());
            verify(client, times(1)).post(eq("api_query_auth"), anyString(), anyMap());
        } finally { pool.shutdownNow(); }
    }
    @Test void tokenCacheRefreshAndRevocationInvalidateSessionsAndDtos() throws Exception {
        var created = service.create(1L,1L); service.callback(state(created), "code");
        assertEquals("access-secret", service.authorizerToken(WechatComponentCryptoTest.APP));
        db.update("update wechat_authorizer set token_expires_at=?", LocalDateTime.now().minusSeconds(1));
        assertEquals("refreshed-secret", service.authorizerToken(WechatComponentCryptoTest.APP));
        assertEquals("refreshed-secret", service.authorizerToken(WechatComponentCryptoTest.APP));
        verify(client, times(1)).post(eq("api_authorizer_token"), anyString(), anyMap());
        verify(client, times(1)).post(eq("api_component_token"), isNull(), anyMap());
        var app = apps.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DramaModels.Miniapp>().eq("appid", WechatComponentCryptoTest.APP));
        String dto = json.writeValueAsString(app);
        assertFalse(dto.contains("secret")); assertFalse(dto.contains("cipher"));
        db.update("insert into miniapp_viewer_session (miniapp_id,token_hash,openid,expires_at) values (?,?,?,?)", app.id, MiniappSessionService.hash("a".repeat(43)), "openid", LocalDateTime.now().plusHours(1));
        String event = "<xml><AppId>" + WechatComponentCryptoTest.APP + "</AppId><CreateTime>" + Instant.now().getEpochSecond() + "</CreateTime><InfoType>unauthorized</InfoType><AuthorizerAppid>" + app.appid + "</AuthorizerAppid></xml>";
        String encrypted = WechatComponentCryptoTest.encrypt(event, WechatComponentCryptoTest.APP);
        service.event("<xml><Encrypt>" + encrypted + "</Encrypt></xml>", WechatComponentCryptoTest.signature(encrypted), "1", "nonce");
        service.event("<xml><Encrypt>" + encrypted + "</Encrypt></xml>", WechatComponentCryptoTest.signature(encrypted), "1", "nonce");
        assertEquals(0, db.queryForObject("select count(*) from miniapp_viewer_session where miniapp_id=?", Integer.class, app.id));
        assertThrows(RuntimeException.class, () -> sessions.authenticate("Bearer " + "a".repeat(43)));
        assertThrows(RuntimeException.class, () -> sessions.login(new DramaModels.LoginRequest(app.appid, "code")));
        assertThrows(RuntimeException.class, () -> service.authorizerToken(app.appid));
        assertNull(db.queryForMap("select * from wechat_authorizer where appid=?", app.appid).get("refresh_cipher"));
        String stale = "<xml><AppId>" + app.appid + "</AppId><CreateTime>1</CreateTime><InfoType>updateauthorized</InfoType><AuthorizerAppid>" + app.appid + "</AuthorizerAppid></xml>";
        String staleEncrypted = WechatComponentCryptoTest.encrypt(stale, app.appid);
        service.event("<xml><Encrypt>" + staleEncrypted + "</Encrypt></xml>", WechatComponentCryptoTest.signature(staleEncrypted), "1", "nonce");
        assertEquals("REVOKED", db.queryForObject("select auth_status from wechat_authorizer where appid=?", String.class, app.appid));
        assertThrows(RuntimeException.class, () -> service.requireAuthorized(app));
    }
    @Test void loggedInNonMemberCannotTrustClientTenant() throws Exception {
        String mobile = "13800000771";
        String verification = registrationVerificationCode(mvc, mobile);
        var registered = mvc.perform(post("/api/auth/register").contentType("application/json")
            .content("{\"mobile\":\"" + mobile + "\",\"verificationCode\":\"" + verification + "\",\"nickname\":\"授权测试\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn();
        var session = registered.getResponse().getCookie("ANT_SHORT_SESSION");
        mvc.perform(get("/api/team-miniapps/authorization/status").cookie(session).header("X-Tenant-Id", "999999"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/team-miniapps/authorization/requests").cookie(session).header("X-Tenant-Id", "999999")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isForbidden());
    }
    static final String MESSAGE_APP = "wxabcdef1234567890";
    void messageApp() throws Exception {
        db.update("insert into wechat_authorizer (appid,tenant_id,auth_status,event_time,event_type) values (?,1,'AUTHORIZED',1,'callback')", MESSAGE_APP);
        db.update("insert into team_miniapp (tenant_id,appid,name,binding_type,enabled,auth_status,created_at) values (1,?,'消息应用','OFFICIAL',true,'AUTHORIZED',?)", MESSAGE_APP, LocalDateTime.now());
        when(client.post(eq("api_get_authorizer_info"), anyString(), anyMap())).thenReturn(json.readTree(
            "{\"authorizer_info\":{\"user_name\":\"gh_fixture\"},\"authorization_info\":{\"authorizer_appid\":\"" + MESSAGE_APP + "\"}}"));
    }
    String messageXml(String recipient, String content) {
        return "<xml><ToUserName>" + recipient + "</ToUserName><FromUserName>openid</FromUserName><CreateTime>1</CreateTime><MsgType>text</MsgType><Content>" + content + "</Content></xml>";
    }
    org.springframework.test.web.servlet.ResultActions messagePost(String appid, String plain, String receiver) throws Exception {
        String encrypted = WechatComponentCryptoTest.encrypt(plain, receiver);
        return mvc.perform(post("/api/wechat/component/messages/" + appid).param("msg_signature", WechatComponentCryptoTest.signature(encrypted))
            .param("timestamp", "1").param("nonce", "nonce").contentType("application/xml")
            .content("<xml><ToUserName>gh_fixture</ToUserName><Encrypt>" + encrypted + "</Encrypt></xml>"));
    }
    @Test void echoRequiresVerifiedSignatureAndNeverDowngradesCipher() throws Exception {
        messageApp();
        String sig = WechatComponentCryptoTest.signature("");
        for (String path : List.of("/api/wechat/component/events", "/api/wechat/component/messages/" + MESSAGE_APP)) {
            mvc.perform(get(path).param("signature", sig).param("timestamp", "1").param("nonce", "nonce").param("echostr", "原样echo"))
                .andExpect(status().isOk()).andExpect(content().string("原样echo"));
            String encrypted = WechatComponentCryptoTest.encrypt("encrypted echo", WechatComponentCryptoTest.APP);
            mvc.perform(get(path).param("msg_signature", WechatComponentCryptoTest.signature(encrypted)).param("timestamp", "1").param("nonce", "nonce").param("echostr", encrypted))
                .andExpect(status().isOk()).andExpect(content().string("encrypted echo"));
            mvc.perform(get(path).param("signature", sig).param("msg_signature", "0".repeat(40)).param("timestamp", "1").param("nonce", "nonce").param("echostr", "not echo"))
                .andExpect(status().isBadRequest());
            mvc.perform(get(path).param("signature", "0".repeat(40)).param("timestamp", "1").param("nonce", "nonce").param("echostr", "not echo"))
                .andExpect(status().isBadRequest());
            mvc.perform(get(path).param("timestamp", "1").param("nonce", "nonce").param("echostr", "not echo"))
                .andExpect(status().isBadRequest());
            mvc.perform(get(path).param("signature", sig).param("timestamp", "1").param("nonce", "nonce").param("echostr", "x".repeat(4097)))
                .andExpect(status().isBadRequest());
        }
        String wrong = WechatComponentCryptoTest.encrypt("echo", MESSAGE_APP);
        mvc.perform(get("/api/wechat/component/events").param("msg_signature", WechatComponentCryptoTest.signature(wrong)).param("timestamp", "1").param("nonce", "nonce").param("echostr", wrong)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wechat/component/events").param("msg_signature", WechatComponentCryptoTest.signature("bad")).param("timestamp", "1").param("nonce", "nonce").param("echostr", "bad")).andExpect(status().isBadRequest());
    }
    @Test void messagesVerifyComponentReceiverAndApplicationOwnership() throws Exception {
        messageApp();
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isOk()).andExpect(content().string(""));
        messagePost(MESSAGE_APP, "<xml><ToUserName>gh_fixture</ToUserName><MsgType>event</MsgType><Event>debug_demo</Event></xml>", WechatComponentCryptoTest.APP)
            .andExpect(status().isOk()).andExpect(content().string("success"));
        messagePost(MESSAGE_APP, messageXml("gh_foreign", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        String mismatch = WechatComponentCryptoTest.encrypt(messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP);
        mvc.perform(post("/api/wechat/component/messages/" + MESSAGE_APP).param("msg_signature", WechatComponentCryptoTest.signature(mismatch))
            .param("timestamp", "1").param("nonce", "nonce").contentType("application/xml")
            .content("<xml><ToUserName>gh_foreign</ToUserName><Encrypt>" + mismatch + "</Encrypt></xml>")).andExpect(status().isBadRequest());
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), MESSAGE_APP).andExpect(status().isBadRequest());
        messagePost("wx1111111111111111", messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        db.update("insert into wechat_authorizer (appid,tenant_id,auth_status,event_time,event_type) values ('wx2222222222222222',2,'AUTHORIZED',1,'callback')");
        db.update("insert into team_miniapp (tenant_id,appid,name,binding_type,enabled,auth_status,created_at) values (2,'wx2222222222222222','另一团队','OFFICIAL',true,'AUTHORIZED',?)", LocalDateTime.now());
        messagePost("wx2222222222222222", messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "TESTCOMPONENT_MSG_TYPE_TEXT"), WechatComponentCryptoTest.APP).andExpect(status().isNotImplemented());
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "QUERY_AUTH_CODE:fixture"), WechatComponentCryptoTest.APP).andExpect(status().isNotImplemented());
        messagePost("wx570bc396a51b8ff8", messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isNotImplemented());
        messagePost(MESSAGE_APP, "<xml><ToUserName>gh_fixture</ToUserName><MsgType>event</MsgType><Event>unknown</Event></xml>", WechatComponentCryptoTest.APP).andExpect(status().isNotImplemented());
        db.update("update team_miniapp set tenant_id=2 where appid=?", MESSAGE_APP);
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        db.update("update team_miniapp set tenant_id=1 where appid=?", MESSAGE_APP);
        db.update("update wechat_authorizer set auth_status='REVOKED' where appid=?", MESSAGE_APP);
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        mvc.perform(get("/api/wechat/component/messages/" + MESSAGE_APP).param("signature", WechatComponentCryptoTest.signature("")).param("timestamp", "1").param("nonce", "nonce").param("echostr", "echo")).andExpect(status().isBadRequest());
    }
    @Test void publicCallbacksRejectCryptoXxeAndOversizedInputsButReceiveTicket() throws Exception {
        messageApp();
        for (String path : List.of("/api/wechat/component/events", "/api/wechat/component/messages/" + MESSAGE_APP)) {
            mvc.perform(post(path).param("msg_signature", WechatComponentCryptoTest.signature("bad")).param("timestamp", "1").param("nonce", "nonce")
                .contentType("application/xml").content("<xml><Encrypt>bad</Encrypt></xml>")).andExpect(status().isBadRequest());
            mvc.perform(post(path).param("msg_signature", "0".repeat(40)).param("timestamp", "1").param("nonce", "nonce")
                .contentType("application/xml").content("<!DOCTYPE xml [<!ENTITY x SYSTEM 'file:///not-read'>]><xml><Encrypt>&x;</Encrypt></xml>")).andExpect(status().isBadRequest());
            mvc.perform(post(path).param("msg_signature", "0".repeat(40)).param("timestamp", "1").param("nonce", "nonce")
                .contentType("application/xml").content("x".repeat(65537))).andExpect(status().isPayloadTooLarge());
            mvc.perform(post(path).param("msg_signature", "0".repeat(40)).param("timestamp", "1").param("nonce", "nonce")
                .contentType("application/xml").content("<xml><Encrypt>bad</Encrypt></xml>")).andExpect(status().isBadRequest());
            mvc.perform(post(path).param("signature", WechatComponentCryptoTest.signature("")).param("timestamp", "1").param("nonce", "nonce")
                .contentType("application/xml").content(messageXml("gh_fixture", "hello"))).andExpect(status().isBadRequest());
        }
        messagePost(MESSAGE_APP, "<!DOCTYPE xml [<!ENTITY x SYSTEM 'file:///not-read'>]><xml><ToUserName>&x;</ToUserName></xml>", WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        String ticket = "<xml><AppId>" + WechatComponentCryptoTest.APP + "</AppId><CreateTime>2</CreateTime><InfoType>component_verify_ticket</InfoType><ComponentVerifyTicket>received-ticket</ComponentVerifyTicket></xml>";
        String encrypted = WechatComponentCryptoTest.encrypt(ticket, WechatComponentCryptoTest.APP);
        mvc.perform(post("/api/wechat/component/events").param("msg_signature", WechatComponentCryptoTest.signature(encrypted)).param("timestamp", "1").param("nonce", "nonce")
            .contentType("application/xml").content("<xml><Encrypt>" + encrypted + "</Encrypt></xml>")).andExpect(status().isOk()).andExpect(content().string("success"));
        assertEquals("received-ticket", WechatComponentCrypto.open(db.queryForObject("select ticket_cipher from wechat_component_state where id=1", String.class), WechatComponentCryptoTest.MASTER, "ticket"));
        mvc.perform(post("/api/wechat/component/messages/" + MESSAGE_APP + "/management")).andExpect(status().isForbidden());
        mvc.perform(get("/api/wechat/component/management")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/wechat/component/events")).andExpect(status().isForbidden());
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(new byte[32], "AES"), new javax.crypto.spec.IvParameterSpec(new byte[16]));
        String badPadding = Base64.getEncoder().encodeToString(cipher.doFinal(new byte[32]));
        mvc.perform(post("/api/wechat/component/messages/" + MESSAGE_APP).param("msg_signature", WechatComponentCryptoTest.signature(badPadding))
            .param("timestamp", "1").param("nonce", "nonce").contentType("application/xml")
            .content("<xml><Encrypt>" + badPadding + "</Encrypt></xml>")).andExpect(status().isBadRequest());
        String valid = WechatComponentCryptoTest.encrypt(messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP);
        mvc.perform(post("/api/wechat/component/messages/" + MESSAGE_APP).param("msg_signature", "0".repeat(40))
            .param("signature", WechatComponentCryptoTest.signature("")).param("timestamp", "1").param("nonce", "nonce").contentType("application/xml")
            .content("<xml><Encrypt>" + valid + "</Encrypt></xml>")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/wechat/component/messages/" + MESSAGE_APP).param("msg_signature", WechatComponentCryptoTest.signature(valid))
            .param("timestamp", "1").param("nonce", "n".repeat(257)).contentType("application/xml")
            .content("<xml><Encrypt>" + valid + "</Encrypt></xml>")).andExpect(status().isBadRequest());
        config.put("WECHAT_COMPONENT_ENABLED", "false");
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        config.put("WECHAT_COMPONENT_ENABLED", "true");
        config.put("WECHAT_COMPONENT_ENCODING_AES_KEY", "invalid");
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
        config.put("WECHAT_COMPONENT_ENCODING_AES_KEY", "B" + WechatComponentCryptoTest.MASTER.substring(1, 43));
        messagePost(MESSAGE_APP, messageXml("gh_fixture", "hello"), WechatComponentCryptoTest.APP).andExpect(status().isBadRequest());
    }
    @Test void managementRequiresPlatformRoleEvenWithAllFeaturePermissions() throws Exception {
        String mobile = "13800000761";
        var registered = mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("mobile", mobile, "verificationCode", registrationVerificationCode(mvc, mobile),
                "nickname", "管理权限测试", "password", "Password123"))))
            .andExpect(status().isOk()).andReturn();
        var session = registered.getResponse().getCookie("ANT_SHORT_SESSION");
        long userId = json.readTree(registered.getResponse().getContentAsString()).at("/data/user/id").asLong();
        var created = mvc.perform(post("/api/tenants").with(com.antshorttv.support.SessionTestSupport.authenticated(session.getValue()))
            .contentType("application/json").content("{\"name\":\"权限测试团队\",\"type\":\"STUDIO\"}"))
            .andExpect(status().isOk()).andReturn();
        long tenantId = json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
        assertTrue(tenantId > 0);
        mvc.perform(get("/api/auth/bootstrap").cookie(session).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedTenant.permissions",
                org.hamcrest.Matchers.hasItems("DRAMA:VIEW", "DRAMA:MANAGE", "DRAMA:PUBLISH", "MINIAPP:MANAGE")));
        // 团队 OWNER 已有全部剧库功能权限；再授予非管理员平台角色的功能权限。
        db.update("insert into platform_role (code,name,status,created_at,updated_at) values ('DRAMA_OPERATOR_TEST','测试操作员','ACTIVE',?,?)", LocalDateTime.now(), LocalDateTime.now());
        long operator = db.queryForObject("select id from platform_role where code='DRAMA_OPERATOR_TEST'", Long.class);
        db.update("insert into platform_role_permission (role_id,permission_id,created_at) select ?,id,? from platform_permission", operator, LocalDateTime.now());
        db.update("insert into platform_user_role (user_id,role_id,created_at) values (?,?,?)", userId, operator, LocalDateTime.now());
        var requests = List.of(
            get("/api/drama-library"), get("/api/drama-library/1"),
            post("/api/drama-library").content("{\"title\":\"测试\"}"),
            put("/api/drama-library/1").content("{\"title\":\"测试\"}"), delete("/api/drama-library/1"),
            get("/api/drama-library/1/episodes"),
            post("/api/drama-library/1/episodes").content("{\"episodeNo\":1,\"title\":\"测试\"}"),
            delete("/api/drama-library/1/episodes/1"), get("/api/team-miniapps"),
            post("/api/team-miniapps").content("{\"appid\":\"wx1234567890abcdef\",\"name\":\"测试\"}"),
            patch("/api/team-miniapps/1").content("{\"enabled\":true}"), get("/api/team-miniapps/1/dramas"),
            put("/api/team-miniapps/1/dramas/1").content("{\"episodeIds\":[1]}"), delete("/api/team-miniapps/1/dramas/1"),
            get("/api/team-miniapps/authorization/status"), post("/api/team-miniapps/authorization/requests"),
            get("/api/team-miniapps/authorization/requests/not-mine"));
        for (var request : requests) {
            mvc.perform(request.with(com.antshorttv.support.SessionTestSupport.authenticated(session.getValue()))
                .header("X-Tenant-Id", tenantId).contentType("application/json"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
        long admin = db.queryForObject("select id from platform_role where code='PLATFORM_ADMIN'", Long.class);
        db.update("insert into platform_user_role (user_id,role_id,created_at) values (?,?,?)", userId, admin, LocalDateTime.now());
        for (String path : List.of("/api/drama-library", "/api/team-miniapps", "/api/team-miniapps/authorization/status")) {
            mvc.perform(get(path).cookie(session).header("X-Tenant-Id", tenantId)).andExpect(status().isOk());
            mvc.perform(get(path).cookie(session).header("X-Tenant-Id", 999999)).andExpect(status().isForbidden());
        }
        var foreignTeam = new com.antshorttv.tenant.TenantEntity();
        foreignTeam.setCode("admin-isolation-team"); foreignTeam.setName("其他团队");
        foreignTeam.setType("TEAM"); foreignTeam.setStatus("ACTIVE");
        foreignTeam.setCreatedAt(LocalDateTime.now()); foreignTeam.setUpdatedAt(LocalDateTime.now()); tenants.insert(foreignTeam);
        mvc.perform(get("/api/drama-library").cookie(session).header("X-Tenant-Id", foreignTeam.getId()))
            .andExpect(status().isForbidden());
        var foreignDrama = library.create(foreignTeam.getId(), new DramaModels.DramaRequest("其他团队剧集", null));
        mvc.perform(get("/api/drama-library/" + foreignDrama.id).cookie(session).header("X-Tenant-Id", tenantId))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/drama-library").with(com.antshorttv.support.SessionTestSupport.authenticated(session.getValue()))
            .header("X-Tenant-Id", tenantId).contentType("application/json").content("{\"title\":\"管理员创建\"}"))
            .andExpect(status().isOk());
        var authorization = mvc.perform(post("/api/team-miniapps/authorization/requests").with(com.antshorttv.support.SessionTestSupport.authenticated(session.getValue())).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andReturn();
        String requestId = json.readTree(authorization.getResponse().getContentAsString()).at("/data/requestId").asText();
        mvc.perform(get("/api/team-miniapps/authorization/requests/" + requestId).cookie(session).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());
        db.update("update platform_role set status='DISABLED' where id=?", admin);
        try {
            mvc.perform(get("/api/drama-library").cookie(session).header("X-Tenant-Id", tenantId)).andExpect(status().isForbidden());
        } finally {
            db.update("update platform_role set status='ACTIVE' where id=?", admin);
        }
        db.update("delete from platform_user_role where user_id=? and role_id=?", userId, admin);
        mvc.perform(get("/api/drama-library").cookie(session).header("X-Tenant-Id", tenantId)).andExpect(status().isForbidden());
    }

    @Test void realSpringSecurityProtectsManagementButValidatesPublicCallbacks() throws Exception {
        mvc.perform(get("/api/team-miniapps/authorization/status").header("X-Tenant-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/team-miniapps/authorization/requests")).andExpect(status().isForbidden());
        mvc.perform(get("/api/team-miniapps/authorization/requests/not-mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wechat/component/events").param("msg_signature", "0".repeat(40)).param("timestamp", "1").param("nonce", "nonce")
            .contentType("application/xml").content("<xml><Encrypt>evil</Encrypt></xml>")).andExpect(status().isBadRequest());
        config.put("WECHAT_COMPONENT_ENABLED", "false");
        assertEquals("DISABLED", service.status().status());
        assertThrows(RuntimeException.class, () -> service.create(1L,1L));
    }
}
