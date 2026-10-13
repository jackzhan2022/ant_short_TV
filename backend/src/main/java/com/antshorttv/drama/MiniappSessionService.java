package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappSessionService {
    private final MiniappMapper apps;
    private final ViewerSessionMapper sessions;
    private final WechatMiniappIdentity identity;
    private final com.antshorttv.tenant.TenantMapper tenants;
    private final MiniappOwnership ownership;
    private final SecureRandom random = new SecureRandom();
    @org.springframework.beans.factory.annotation.Autowired
    private WechatComponentService component;

    public MiniappSessionService(MiniappMapper apps, ViewerSessionMapper sessions, WechatMiniappIdentity identity,
        com.antshorttv.tenant.TenantMapper tenants, MiniappOwnership ownership) {
        this.apps = apps; this.sessions = sessions; this.identity = identity; this.tenants = tenants;
        this.ownership = ownership;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (component != null) component.lockLifecycle();
        Miniapp app = apps.selectOne(new QueryWrapper<Miniapp>().eq("appid", request.appid()).eq("enabled", true));
        requireActiveTeam(app);
        String openid = "OFFICIAL".equals(app.bindingType)
            ? component.login(app, request.code()) : identity.exchange(app.appid, request.code());
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ViewerSession session = new ViewerSession(); session.miniappId = app.id; session.openid = openid;
        session.tokenHash = hash(token); session.expiresAt = LocalDateTime.now().plusHours(2);
        sessions.insert(session); return new LoginResponse(token, "Bearer", session.expiresAt);
    }

    public Miniapp authenticate(String authorization) {
        String token = token(authorization);
        ViewerSession session = sessions.selectOne(new QueryWrapper<ViewerSession>().eq("token_hash", hash(token))
            .gt("expires_at", LocalDateTime.now()));
        if (session == null) throw WechatMiniappIdentity.unauthorized();
        Miniapp app = apps.selectOne(new QueryWrapper<Miniapp>().eq("id", session.miniappId).eq("enabled", true));
        requireActiveTeam(app);
        return app;
    }

    public void logout(String authorization) {
        sessions.delete(new QueryWrapper<ViewerSession>().eq("token_hash", hash(token(authorization))));
    }

    private void requireActiveTeam(Miniapp app) {
        if (app == null) throw WechatMiniappIdentity.unauthorized();
        if ("OFFICIAL".equals(app.bindingType)) component.requireAuthorized(app);
        else ownership.require(app.appid, app.tenantId);
        var team = tenants.selectById(app.tenantId);
        if (team == null || team.getDeletedAt() != null || !"ACTIVE".equals(team.getStatus())) {
            throw WechatMiniappIdentity.unauthorized();
        }
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}")) {
            throw WechatMiniappIdentity.unauthorized();
        }
        return authorization.substring(7);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
