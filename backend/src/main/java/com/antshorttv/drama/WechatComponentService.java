package com.antshorttv.drama;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class WechatComponentService {
    private final Environment env;
    private final JdbcTemplate db;
    private final WechatComponentClient client;
    private final TransactionTemplate tx;
    public WechatComponentService(Environment env, JdbcTemplate db, WechatComponentClient client, PlatformTransactionManager manager) {
        this.env = env; this.db = db; this.client = client; this.tx = new TransactionTemplate(manager);
    }
    private String setting(String name) { return env.getProperty("WECHAT_COMPONENT_" + name, ""); }
    public record Status(String status, boolean available) {}
    public record Created(String requestId, String authorizationUrl, LocalDateTime expiresAt) {}
    public record RequestStatus(String requestId, String status, String appid, LocalDateTime expiresAt) {}
    public Status status() {
        if (!Boolean.parseBoolean(setting("ENABLED"))) return new Status("DISABLED", false);
        try {
            var callback = java.net.URI.create(setting("CALLBACK_URL"));
            if (!"https".equals(callback.getScheme()) || callback.getHost() == null || callback.getUserInfo() != null) return new Status("NOT_CONFIGURED", false);
            if (Base64.getDecoder().decode(setting("ENCODING_AES_KEY") + "=").length != 32) return new Status("NOT_CONFIGURED", false);
            if (!setting("APPID").matches("wx[0-9a-fA-F]{16}") || setting("SECRET").isBlank()
                || setting("EVENT_TOKEN").isBlank() || setting("ENCODING_AES_KEY").length() != 43
                || !setting("CALLBACK_URL").startsWith("https://") || setting("CALLBACK_URL").contains("?")
                || setting("CALLBACK_URL").contains("#")) return new Status("NOT_CONFIGURED", false);
            WechatComponentCrypto.key(setting("MASTER_KEY"));
        } catch (Exception e) { return new Status("NOT_CONFIGURED", false); }
        var row = component(false);
        return row.get("ticket_cipher") == null ? new Status("WAITING_TICKET", false) : new Status("READY", true);
    }
    private void configured() {
        Status s = status();
        if (!s.available()) throw DramaLibraryService.invalid("第三方授权暂不可用：" + s.status());
    }
    private Map<String, Object> component(boolean lock) {
        return db.queryForMap("select * from wechat_component_state where id=1" + (lock ? " for update" : ""));
    }
    private String seal(String value, String context) { return WechatComponentCrypto.seal(value, setting("MASTER_KEY"), context); }
    private String open(Object value, String context) { return WechatComponentCrypto.open((String)value, setting("MASTER_KEY"), context); }
    private LocalDateTime expiry(JsonNode data) {
        int seconds = data.path("expires_in").asInt();
        if (seconds <= 60 || seconds > 86400) throw WechatComponentCrypto.rejected();
        return LocalDateTime.now().plusSeconds(seconds - 60);
    }
    private String required(JsonNode data, String field) {
        String value = data.path(field).asText();
        if (value.isBlank() || value.length() > 8192) throw WechatComponentCrypto.rejected();
        return value;
    }
    private LocalDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return ((java.sql.Timestamp) value).toLocalDateTime();
    }
    public String componentToken() {
        configured();
        return tx.execute(s -> {
            var row = component(true);
            if (time(row.get("token_expires_at")) != null && time(row.get("token_expires_at")).isAfter(LocalDateTime.now()))
                return open(row.get("token_cipher"), "component-token");
            var result = client.post("api_component_token", null, Map.of("component_appid", setting("APPID"),
                "component_appsecret", setting("SECRET"), "component_verify_ticket", open(row.get("ticket_cipher"), "ticket")));
            String token = required(result, "component_access_token");
            db.update("update wechat_component_state set token_cipher=?,token_expires_at=? where id=1", seal(token, "component-token"), expiry(result));
            return token;
        });
    }
    public Created create(Long tenant, Long operator) {
        String token = componentToken();
        var pre = client.post("api_create_preauthcode", token, Map.of("component_appid", setting("APPID")));
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString(); LocalDateTime expires = expiry(pre);
        String preauth = required(pre, "pre_auth_code");
        db.update("insert into wechat_authorization_request (id,tenant_id,operator_id,state_hash,preauth_hash,status,created_at,expires_at) values (?,?,?,?,?,'PENDING',?,?)",
            id, tenant, operator, MiniappSessionService.hash(state), MiniappSessionService.hash(preauth), LocalDateTime.now(), expires);
        String redirect = setting("CALLBACK_URL") + "?state=" + state;
        return new Created(id, "https://mp.weixin.qq.com/cgi-bin/componentloginpage?component_appid=" + WechatComponentClient.encode(setting("APPID"))
            + "&pre_auth_code=" + WechatComponentClient.encode(preauth) + "&redirect_uri=" + WechatComponentClient.encode(redirect) + "&auth_type=2", expires);
    }
    public RequestStatus request(Long tenant, String id) {
        var rows = db.queryForList("select id,status,appid,expires_at from wechat_authorization_request where id=? and tenant_id=?", id, tenant);
        if (rows.isEmpty()) throw DramaLibraryService.missing();
        var row = rows.get(0); LocalDateTime expires = time(row.get("expires_at"));
        String status = (String)row.get("status");
        if ("PENDING".equals(status) && !expires.isAfter(LocalDateTime.now())) status = "EXPIRED";
        if ("PROCESSING".equals(status) && !expires.plusMinutes(2).isAfter(LocalDateTime.now())) status = "FAILED";
        return new RequestStatus(id, status, (String)row.get("appid"), expires);
    }
    public void callback(String state, String code) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}") || code == null || code.isBlank() || code.length() > 2048) throw WechatComponentCrypto.rejected();
        configured();
        var row = tx.execute(s -> {
            component(true);
            var rows = db.queryForList("select * from wechat_authorization_request where state_hash=? for update", MiniappSessionService.hash(state));
            if (rows.isEmpty()) throw WechatComponentCrypto.rejected();
            var request = rows.get(0);
            if (!time(request.get("expires_at")).isAfter(LocalDateTime.now())) throw WechatComponentCrypto.rejected();
            // 微信授权事件可能先于浏览器回调完成绑定；只确认结果，不再次兑换授权码或修改绑定。
            if ("SUCCEEDED".equals(request.get("status"))) {
                if (db.queryForObject("select count(*) from wechat_authorizer a join team_miniapp m on m.appid=a.appid and m.tenant_id=a.tenant_id where a.appid=? and a.tenant_id=? and a.auth_status='AUTHORIZED' and m.auth_status='AUTHORIZED' and m.binding_type='OFFICIAL'",
                    Long.class, request.get("appid"), request.get("tenant_id")) != 1) throw WechatComponentCrypto.rejected();
                return null;
            }
            if (!"PENDING".equals(request.get("status"))) throw WechatComponentCrypto.rejected();
            db.update("update wechat_authorization_request set status='PROCESSING' where id=?", request.get("id"));
            return request;
        });
        if (row == null) return;
        try {
            tx.executeWithoutResult(s -> {
                component(true);
                String appid = apply(code, ((Number)row.get("tenant_id")).longValue(), componentToken(), null,
                    time(row.get("created_at")).atZone(java.time.ZoneId.systemDefault()).toEpochSecond(), "callback");
                db.update("update wechat_authorization_request set status='SUCCEEDED',appid=? where id=?", appid, row.get("id"));
            });
        } catch (RuntimeException e) {
            db.update("update wechat_authorization_request set status='FAILED' where id=?", row.get("id"));
            throw WechatComponentCrypto.rejected();
        }
    }
    private String apply(String code, Long tenant, String token, String expectedAppid, long eventTime, String eventType) {
        var auth = client.post("api_query_auth", token, Map.of("component_appid", setting("APPID"), "authorization_code", code)).path("authorization_info");
        String appid = required(auth, "authorizer_appid");
        if (!appid.matches("wx[0-9a-fA-F]{16}") || expectedAppid != null && !expectedAppid.equals(appid)) throw WechatComponentCrypto.rejected();
        var info = client.post("api_get_authorizer_info", token, Map.of("component_appid", setting("APPID"), "authorizer_appid", appid));
        if (!info.path("authorizer_info").path("MiniProgramInfo").isObject()
            || !appid.equals(info.path("authorization_info").path("authorizer_appid").asText()) || !auth.path("func_info").isArray()) throw WechatComponentCrypto.rejected();
        var existing = db.queryForList("select * from wechat_authorizer where appid=? for update", appid);
        if (!existing.isEmpty()) {
            var old = existing.get(0);
            if (old.get("tenant_id") != null && ((Number)old.get("tenant_id")).longValue() != tenant) throw WechatComponentCrypto.rejected();
            long previous = ((Number)old.get("event_time")).longValue();
            if (eventTime < previous || eventTime == previous && "REVOKED".equals(old.get("auth_status"))) throw WechatComponentCrypto.rejected();
        }
        var apps = db.queryForList("select * from team_miniapp where appid=? for update", appid);
        if (!apps.isEmpty() && ((Number)apps.get(0).get("tenant_id")).longValue() != tenant) throw WechatComponentCrypto.rejected();
        String access = seal(required(auth, "authorizer_access_token"), appid + ":access");
        String refresh = seal(required(auth, "authorizer_refresh_token"), appid + ":refresh");
        LocalDateTime expires = expiry(auth);
        String scopes = auth.path("func_info").toString();
        if (existing.isEmpty()) db.update("insert into wechat_authorizer (appid,tenant_id,auth_status,access_cipher,refresh_cipher,token_expires_at,scopes,event_time,event_type) values (?,?,'AUTHORIZED',?,?,?,?,?,?)", appid, tenant, access, refresh, expires, scopes, eventTime, eventType);
        else db.update("update wechat_authorizer set tenant_id=?,auth_status='AUTHORIZED',access_cipher=?,refresh_cipher=?,token_expires_at=?,scopes=?,event_time=?,event_type=? where appid=?", tenant, access, refresh, expires, scopes, eventTime == 0 ? existing.get(0).get("event_time") : eventTime, eventType, appid);
        String name = required(info.path("authorizer_info"), "nick_name");
        if (name.length() > 100) name = name.substring(0, 100);
        if (apps.isEmpty()) db.update("insert into team_miniapp (tenant_id,appid,name,binding_type,enabled,auth_status,created_at) values (?,?,?,'OFFICIAL',true,'AUTHORIZED',?)", tenant, appid, name, LocalDateTime.now());
        else db.update("update team_miniapp set name=?,binding_type='OFFICIAL',auth_status='AUTHORIZED' where appid=?", name, appid);
        return appid;
    }
    public String echo(String appid, String signature, String messageSignature, String timestamp, String nonce, String echo) {
        if (!Boolean.parseBoolean(setting("ENABLED")) || echo == null || echo.isBlank() || echo.length() > 4096) throw WechatComponentCrypto.rejected();
        // 密文签名存在时禁止降级到三参数签名。
        String result;
        if (messageSignature != null) result = WechatComponentCrypto.decrypt(echo, messageSignature, timestamp, nonce,
            setting("EVENT_TOKEN"), setting("ENCODING_AES_KEY"), setting("APPID"));
        else {
            WechatComponentCrypto.verify(signature, timestamp, nonce, setting("EVENT_TOKEN"), null);
            result = echo;
        }
        if (appid != null) requireMessageApp(appid);
        return result;
    }
    private void requireMessageApp(String appid) {
        if (Set.of("wx570bc396a51b8ff8", "wx9252c5e0bb1836fc", "wx8e1097c5bc82cde9", "wx14550af28c71a144", "wxa35b9c23cfe664eb",
            "wxd101a85aa106f53e", "wxc39235c15087f6f3", "wx7720d01d4b2a4500", "wx05d483572dcd5d8b", "wx5910277cae6fd970").contains(appid))
            throw unsupportedMessage();
        if (appid == null || !appid.matches("wx[0-9a-fA-F]{16}") || db.queryForObject(
            "select count(*) from wechat_authorizer a join team_miniapp m on m.appid=a.appid and m.tenant_id=a.tenant_id where a.appid=? and a.auth_status='AUTHORIZED' and m.auth_status='AUTHORIZED' and m.binding_type='OFFICIAL'", Long.class, appid) != 1)
            throw WechatComponentCrypto.rejected();
    }
    private org.springframework.web.server.ResponseStatusException unsupportedMessage() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_IMPLEMENTED,
            "不支持此消息业务、客服回复或全网自动化测试。");
    }
    public String message(String appid, String xml, String signature, String timestamp, String nonce) {
        if (!Boolean.parseBoolean(setting("ENABLED"))) throw WechatComponentCrypto.rejected();
        var outer = WechatComponentCrypto.xml(xml);
        String encrypted = WechatComponentCrypto.field(outer, "Encrypt");
        // 官方第三方推送的AES尾部是component appid，而不是URL中的授权方appid。
        var plain = WechatComponentCrypto.xml(WechatComponentCrypto.decrypt(encrypted, signature, timestamp, nonce,
            setting("EVENT_TOKEN"), setting("ENCODING_AES_KEY"), setting("APPID")));
        return tx.execute(s -> {
            component(true);
            requireMessageApp(appid);
            // 不新增迁移：通过官方账号信息核实原始ID，不能把ToUserName当作appid。
            var info = client.post("api_get_authorizer_info", componentToken(), Map.of("component_appid", setting("APPID"), "authorizer_appid", appid));
            if (!appid.equals(info.path("authorization_info").path("authorizer_appid").asText())
                || !required(info.path("authorizer_info"), "user_name").equals(WechatComponentCrypto.field(plain, "ToUserName"))
                || !WechatComponentCrypto.field(plain, "ToUserName").equals(WechatComponentCrypto.field(outer, "ToUserName"))) throw WechatComponentCrypto.rejected();
            String type = WechatComponentCrypto.field(plain, "MsgType");
            if ("event".equals(type) && "debug_demo".equals(WechatComponentCrypto.field(plain, "Event"))) return "success";
            // 普通消息仅确认已安全接收且不回复，未实现客服或业务事件处理。
            if (Set.of("text", "image", "voice", "video", "shortvideo", "location", "link").contains(type)) {
                if ("text".equals(type)) {
                    String content = WechatComponentCrypto.field(plain, "Content");
                    if (content.equals("TESTCOMPONENT_MSG_TYPE_TEXT") || content.startsWith("QUERY_AUTH_CODE:")) throw unsupportedMessage();
                }
                WechatComponentCrypto.field(plain, "FromUserName");
                try { if (Long.parseLong(WechatComponentCrypto.field(plain, "CreateTime")) <= 0) throw WechatComponentCrypto.rejected(); }
                catch (NumberFormatException e) { throw WechatComponentCrypto.rejected(); }
                return "";
            }
            throw unsupportedMessage();
        });
    }
    public void event(String xml, String signature, String timestamp, String nonce) {
        if (!Boolean.parseBoolean(setting("ENABLED"))) throw WechatComponentCrypto.rejected();
        String encrypted = WechatComponentCrypto.field(WechatComponentCrypto.xml(xml), "Encrypt");
        var d = WechatComponentCrypto.xml(WechatComponentCrypto.decrypt(encrypted, signature, timestamp, nonce,
            setting("EVENT_TOKEN"), setting("ENCODING_AES_KEY"), setting("APPID")));
        if (!setting("APPID").equals(WechatComponentCrypto.field(d, "AppId"))) throw WechatComponentCrypto.rejected();
        String type = WechatComponentCrypto.field(d, "InfoType");
        long created;
        try { created = Long.parseLong(WechatComponentCrypto.field(d, "CreateTime")); }
        catch (Exception e) { throw WechatComponentCrypto.rejected(); }
        if (created <= 0 || created > java.time.Instant.now().getEpochSecond() + 300) throw WechatComponentCrypto.rejected();
        tx.executeWithoutResult(s -> {
            var component = component(true);
            if ("component_verify_ticket".equals(type)) {
                if (created > ((Number)component.get("ticket_time")).longValue()) db.update("update wechat_component_state set ticket_cipher=?,ticket_time=? where id=1", seal(WechatComponentCrypto.field(d, "ComponentVerifyTicket"), "ticket"), created);
                return;
            }
            if (!Set.of("authorized", "updateauthorized", "unauthorized").contains(type)) throw WechatComponentCrypto.rejected();
            String appid = WechatComponentCrypto.field(d, "AuthorizerAppid");
            if (!appid.matches("wx[0-9a-fA-F]{16}")) throw WechatComponentCrypto.rejected();
            var rows = db.queryForList("select * from wechat_authorizer where appid=? for update", appid);
            if (!rows.isEmpty()) {
                long previous = ((Number)rows.get(0).get("event_time")).longValue();
                if (created < previous || created == previous && (!"unauthorized".equals(type) || "unauthorized".equals(rows.get(0).get("event_type")))) return;
            }
            if ("unauthorized".equals(type)) {
                if (rows.isEmpty()) db.update("insert into wechat_authorizer (appid,auth_status,event_time,event_type) values (?,'REVOKED',?,'unauthorized')", appid, created);
                else db.update("update wechat_authorizer set auth_status='REVOKED',access_cipher=null,refresh_cipher=null,token_expires_at=null,event_time=?,event_type='unauthorized' where appid=?", created, appid);
                db.update("update team_miniapp set auth_status='REVOKED',enabled=false where appid=? and binding_type='OFFICIAL'", appid);
                db.update("delete from miniapp_viewer_session where miniapp_id in (select id from team_miniapp where appid=? and binding_type='OFFICIAL')", appid);
                return;
            }
            try {
                if (Long.parseLong(WechatComponentCrypto.field(d, "AuthorizationCodeExpiredTime")) <= java.time.Instant.now().getEpochSecond()) throw WechatComponentCrypto.rejected();
            } catch (NumberFormatException e) { throw WechatComponentCrypto.rejected(); }
            Long tenant = rows.isEmpty() || rows.get(0).get("tenant_id") == null ? null : ((Number)rows.get(0).get("tenant_id")).longValue();
            if (tenant == null) {
                var requests = db.queryForList("select * from wechat_authorization_request where preauth_hash=? and status='PENDING' and expires_at>? for update",
                    MiniappSessionService.hash(WechatComponentCrypto.field(d, "PreAuthCode")), LocalDateTime.now());
                if (requests.isEmpty()) return;
                tenant = ((Number)requests.get(0).get("tenant_id")).longValue();
                apply(WechatComponentCrypto.field(d, "AuthorizationCode"), tenant, componentToken(), appid, created, type);
                db.update("update wechat_authorization_request set status='SUCCEEDED',appid=? where id=?", appid, requests.get(0).get("id"));
            } else apply(WechatComponentCrypto.field(d, "AuthorizationCode"), tenant, componentToken(), appid, created, type);
        });
    }
    public void lockLifecycle() { component(true); }
    public void requireAuthorized(DramaModels.Miniapp app) {
        if (!"OFFICIAL".equals(app.bindingType)) return;
        configured();
        if (!"AUTHORIZED".equals(app.authStatus) || db.queryForObject("select count(*) from wechat_authorizer where appid=? and tenant_id=? and auth_status='AUTHORIZED'", Long.class, app.appid, app.tenantId) != 1) throw WechatMiniappIdentity.unauthorized();
    }
    public String login(DramaModels.Miniapp app, String code) {
        requireAuthorized(app);
        return client.login(app.appid, code, setting("APPID"), componentToken());
    }
    public String authorizerToken(String appid) {
        String component = componentToken();
        return tx.execute(s -> {
            component(true);
            var row = db.queryForMap("select * from wechat_authorizer where appid=? for update", appid);
            if (!"AUTHORIZED".equals(row.get("auth_status"))) throw WechatMiniappIdentity.unauthorized();
            if (time(row.get("token_expires_at")) != null && time(row.get("token_expires_at")).isAfter(LocalDateTime.now())) return open(row.get("access_cipher"), appid + ":access");
            var result = client.post("api_authorizer_token", component, Map.of("component_appid", setting("APPID"), "authorizer_appid", appid, "authorizer_refresh_token", open(row.get("refresh_cipher"), appid + ":refresh")));
            String access = required(result, "authorizer_access_token");
            db.update("update wechat_authorizer set access_cipher=?,refresh_cipher=?,token_expires_at=? where appid=?", seal(access, appid + ":access"), seal(required(result, "authorizer_refresh_token"), appid + ":refresh"), expiry(result), appid);
            return access;
        });
    }
}
