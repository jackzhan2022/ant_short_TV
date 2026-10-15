package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.rbac.TenantPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.antshorttv.drama.WechatCodeModels.*;

@Service
public class WechatCodeService {
    private final MiniappMapper apps;
    private final JdbcTemplate db;
    private final WechatComponentService component;
    private final WechatComponentClient client;
    private final PlatformPermissionGuard platform;
    private final TenantPermissionGuard permissions;
    private final ObjectMapper json;
    // 只防同一 JVM 内进行中的上传，不是跨实例锁或持久化幂等记录。
    private final Set<String> uploading = ConcurrentHashMap.newKeySet();

    public WechatCodeService(MiniappMapper apps, JdbcTemplate db, WechatComponentService component,
                             WechatComponentClient client, PlatformPermissionGuard platform,
                             TenantPermissionGuard permissions, ObjectMapper json) {
        this.apps = apps; this.db = db; this.component = component; this.client = client;
        this.platform = platform; this.permissions = permissions; this.json = json;
    }

    public List<Template> templates(Long tenant) {
        platform.requireAdministrator();
        permissions.require(tenant, "MINIAPP:MANAGE");
        return ordinaryTemplates();
    }

    private List<Template> ordinaryTemplates() {
        var result = client.getTemplates(component.componentToken());
        if (result == null || !result.path("errcode").isIntegralNumber() || !result.path("errcode").canConvertToInt() || result.path("errcode").intValue() != 0
            || !result.path("template_list").isArray()) throw upstream();
        var templates = new ArrayList<Template>();
        for (var item : result.path("template_list")) {
            if (!item.path("template_type").isIntegralNumber() || !item.path("template_type").canConvertToInt()
                || item.path("template_type").intValue() != 0) continue;
            var id = item.path("template_id");
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 0) throw upstream();
            templates.add(new Template(id.longValue(), text(item, "user_version", 64), text(item, "user_desc", 1024),
                text(item, "source_miniprogram_appid", 64), text(item, "source_miniprogram", 200)));
        }
        return templates;
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String field, int limit) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isTextual()) throw upstream();
        return value.textValue().substring(0, Math.min(limit, value.textValue().length()));
    }

    private DramaModels.Miniapp target(Long requestedTenant, Long id, boolean qr) {
        platform.requireAdministrator();
        // 先验证请求团队权限，再从库取得目标归属；不能以客户端 tenant/appid 替代真实归属。
        permissions.require(requestedTenant, "MINIAPP:MANAGE");
        var app = apps.selectById(id);
        if (app == null || !Objects.equals(requestedTenant, app.tenantId)) throw DramaLibraryService.missing();
        permissions.require(app.tenantId, "MINIAPP:MANAGE");
        if (!Boolean.TRUE.equals(app.enabled) || !"OFFICIAL".equals(app.bindingType)
            || !"AUTHORIZED".equals(app.authStatus) || app.appid == null || !app.appid.matches("wx[0-9a-fA-F]{16}")) throw forbidden();
        var auth = db.queryForList("select scopes from wechat_authorizer where appid=? and tenant_id=? and auth_status='AUTHORIZED'", app.appid, app.tenantId);
        if (auth.size() != 1) throw forbidden();
        boolean allowed = false;
        try {
            var scopes = json.readTree((String)auth.get(0).get("scopes"));
            if (scopes == null || !scopes.isArray()) throw forbidden();
            for (var scope : scopes) {
                var value = scope.path("funcscope_category").path("id");
                if (value.isIntegralNumber() && value.canConvertToInt()
                    && (value.intValue() == 18 || qr && value.intValue() == 86)) allowed = true;
            }
        } catch (Exception e) { throw forbidden(); }
        if (!allowed) throw forbidden();
        component.requireAuthorized(app);
        return app;
    }

    public CommitResult commit(Long tenant, Long id, CommitRequest request) {
        var app = target(tenant, id, false);
        if (request == null || request.templateId() == null || request.templateId() < 0
            || request.version() == null || request.version().isBlank() || request.version().length() > 64
            || request.description() == null || request.description().isBlank() || request.description().length() > 1024)
            throw DramaLibraryService.invalid("上传参数不正确。");
        if (!uploading.add(app.appid)) throw new CodeException(HttpStatus.CONFLICT, "WECHAT_COMMIT_IN_PROGRESS", "该小程序正在上传，请勿重复操作。");
        try {
            if (ordinaryTemplates().stream().noneMatch(t -> t.templateId() == request.templateId()))
                throw DramaLibraryService.invalid("普通模板库中不存在该模板。");
            String ext = json.createObjectNode().put("extEnable", true).put("extAppid", app.appid).toString();
            String token = component.authorizerToken(app.appid);
            // 获取凭据可能耗时，再次核实数据库授权状态；不持有数据库事务跨外部上传。
            target(tenant, id, false);
            com.fasterxml.jackson.databind.JsonNode result;
            try {
                result = client.commitCode(token, Map.of("template_id", request.templateId(), "user_version", request.version(),
                    "user_desc", request.description(), "ext_json", ext));
            } catch (CodeException e) { throw e; }
            catch (RuntimeException e) { throw unknown(); }
            if (result == null || !result.path("errcode").isIntegralNumber() || !result.path("errcode").canConvertToInt()) throw unknown();
            if (result.path("errcode").intValue() != 0) throw rejected(result.path("errcode").intValue());
            return new CommitResult("SUCCEEDED");
        } finally { uploading.remove(app.appid); }
    }

    public QrCode trialQrCode(Long tenant, Long id, String path) {
        var app = target(tenant, id, true);
        if (path != null && (path.length() > 1024 || path.chars().anyMatch(Character::isISOControl)))
            throw DramaLibraryService.invalid("体验版页面路径不正确。");
        String token = component.authorizerToken(app.appid);
        target(tenant, id, true);
        return client.trialQrCode(token, path);
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "小程序未启用有效官方授权或缺少代码管理权限集。");
    }
}
