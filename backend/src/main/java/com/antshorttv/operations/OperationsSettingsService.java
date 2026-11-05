package com.antshorttv.operations;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.monitoring.MonitoringProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@DependsOnDatabaseInitialization
public class OperationsSettingsService {
    private static final Set<String> BOOLEANS=Set.of("telemetryEnabled","notificationsEnabled","externalProbeConfigured","persistentDigestEnabled");
    private static final Set<String> RETENTION=Set.of("minuteRetentionDays","hourlyRetentionDays","eventRetentionDays","projectionRetentionDays","alertRetentionDays","notificationRetentionDays");
    private final OperationsDatabase database;
    private final ObjectMapper json;
    private final MonitoringProperties monitoring;
    private volatile Map<String,Object> cached;
    public OperationsSettingsService(OperationsDatabase database,ObjectMapper json,MonitoringProperties monitoring) {
        this.database=database;this.json=json;this.monitoring=monitoring;
        cached=defaults();
    }
    @PostConstruct public void initialize() {
        try { current(); } catch (org.springframework.dao.DataAccessException ignored) { /* Independent observer retries when storage returns. */ }
    }
    private Map<String,Object> defaults() {
        var map=new LinkedHashMap<String,Object>();
        map.put("telemetryEnabled",true); map.put("notificationsEnabled",false);
        map.put("observationUntil",Instant.now().plusSeconds(86400).toString());
        map.put("persistentDigestEnabled",true);map.put("externalProbeConfigured",false);map.put("refreshSeconds",15);
        map.put("minuteRetentionDays",7);map.put("hourlyRetentionDays",90);map.put("eventRetentionDays",14);
        map.put("projectionRetentionDays",30);map.put("alertRetentionDays",90);map.put("notificationRetentionDays",30);map.put("version",0L);
        return map;
    }
    public Map<String,Object> current() {
        var rows=database.jdbc().queryForList("select value_json,version from ops_runtime_setting where setting_key='config'");
        if(rows.isEmpty()) {
            try { database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values('config',?,0,?)",encode(cached),Timestamp.from(Instant.now())); }
            catch(DuplicateKeyException ignored) { return current(); }
        } else {
            var value=defaults(); value.putAll(decode(rows.get(0).get("value_json").toString()));
            value.put("version",((Number)rows.get(0).get("version")).longValue());cached=Map.copyOf(value);
        }
        var result=new LinkedHashMap<>(cached);
        result.put("channelConfigured",monitoring.feishuWebhookUrl()!=null&&!monitoring.feishuWebhookUrl().isBlank());
        result.put("signatureConfigured",monitoring.feishuSecret()!=null&&!monitoring.feishuSecret().isBlank());
        result.put("instanceId",instanceId());
        return result;
    }
    public Map<String,Object> update(Map<String,Object> body) {
        current(); var value=new LinkedHashMap<>(cached);
        for(var entry:body.entrySet()) {
            String key=entry.getKey();Object input=entry.getValue();
            if("version".equals(key)) continue;
            if(BOOLEANS.contains(key)) { if(!(input instanceof Boolean)) invalid("开关必须为布尔值"); }
            else if(RETENTION.contains(key)) { integer(input,1,90,key); }
            else if("refreshSeconds".equals(key)) { integer(input,5,300,key); }
            else if("observationUntil".equals(key)) {
                try { Instant at=Instant.parse(String.valueOf(input)); if(at.isAfter(Instant.now().plusSeconds(90L*86400))) invalid("观察期限最长90天"); }
                catch(java.time.DateTimeException e) { invalid("观察期时间格式不正确"); }
            } else invalid("不支持的监控设置字段："+key);
            value.put(key,input);
        }
        long version=number(body.get("version"),"version");
        if(number(value.get("hourlyRetentionDays"),"")<number(value.get("minuteRetentionDays"),"")) invalid("小时保留期不能短于分钟保留期");
        if(number(value.get("alertRetentionDays"),"")<number(value.get("notificationRetentionDays"),"")) invalid("告警保留期不能短于通知保留期");
        value.put("version",version+1);
        int count=database.jdbc().update("update ops_runtime_setting set value_json=?,version=version+1,updated_at=? where setting_key='config' and version=?",encode(value),Timestamp.from(Instant.now()),version);
        if(count!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"监控设置已更新，请刷新后重试");
        cached=Map.copyOf(value);return current();
    }
    public boolean telemetryEnabled() { return Boolean.TRUE.equals(cached.get("telemetryEnabled")); }
    public boolean notificationsEnabled(Instant now) {
        return Boolean.TRUE.equals(cached.get("notificationsEnabled")) && monitoring.feishuWebhookUrl()!=null&&!monitoring.feishuWebhookUrl().isBlank()
            && !now.isBefore(Instant.parse(cached.get("observationUntil").toString()));
    }
    public boolean persistentDigestEnabled() { return telemetryEnabled()&&Boolean.TRUE.equals(cached.get("persistentDigestEnabled")); }
    public String instanceId() { String id=monitoring.instanceId(); return id==null?"local":id.substring(0,Math.min(128,id.length())); }
    private String encode(Map<String,Object> map) { try{return json.writeValueAsString(map);}catch(Exception e){throw new IllegalStateException(e);} }
    private Map<String,Object> decode(String value) {try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid operations config",e);} }
    private static long number(Object value,String field) { if(!(value instanceof Number n)||n.doubleValue()!=n.longValue()) {invalid(field+"必须为整数");return 0;} return ((Number)value).longValue(); }
    private static void integer(Object value,int min,int max,String field) {long n=number(value,field);if(n<min||n>max)invalid(field+"超出允许范围");}
    private static void invalid(String message) {throw new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
}
