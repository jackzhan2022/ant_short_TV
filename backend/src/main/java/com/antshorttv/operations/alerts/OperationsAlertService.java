package com.antshorttv.operations.alerts;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.monitoring.MonitoringEvent;
import com.antshorttv.operations.OperationsDatabase;
import com.antshorttv.operations.OperationsMetricSource;
import com.antshorttv.operations.OperationsSettingsService;
import com.antshorttv.operations.OperationsTime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** One persisted state per explicitly scoped rule; absent data never constitutes healthy evidence. */
@Service
@DependsOnDatabaseInitialization
public class OperationsAlertService {
    private static final Set<String> METRICS = Set.of("http.errorRate", "http.p95Ms", "ai.technicalFailureRate",
        "ai.businessFailureRate", "queue.oldestRunnableSeconds", "execution.expiredLeases", "execution.phaseAgeSeconds", "jobs.stalled",
        "db.pending", "jvm.heapPercent", "settlement.overdue", "monitoring.faults");
    private static final String RULE_COLUMNS = "id,name,description,enabled,severity,metric,operator,threshold_value,recovery_threshold,window_minutes,sustained_minutes,minimum_samples,cooldown_minutes,dimensions_json,notify,version,created_at,updated_at";
    private final OperationsDatabase database;
    private final OperationsSettingsService settings;
    private final OperationsMetricSource source;
    private final ObjectMapper json;
    private final OperationsLease leases;
    private final Clock clock;
    private volatile boolean defaultsReady;

    @Autowired
    public OperationsAlertService(OperationsDatabase database, OperationsSettingsService settings,
        OperationsMetricSource source, ObjectMapper json) {
        this(database, settings, source, json, Clock.systemUTC());
    }
    OperationsAlertService(OperationsDatabase database, OperationsSettingsService settings,
        OperationsMetricSource source, ObjectMapper json, Clock clock) {
        this.database=database; this.settings=settings; this.source=source; this.json=json; this.clock=clock;
        leases=new OperationsLease(database);
    }

    @PostConstruct
    public void initializeDefaults() {
        if(defaultsReady) return;
        try { seedDefaults(); }
        catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
            // The independent evaluator retries; observing an unavailable store must not abort business startup.
        }
    }
    private void seedDefaults() {
        Instant now=clock.instant();
        var lease=leases.acquire("alerts-default-rules",settings.instanceId(),now,Duration.ofSeconds(15));
        if(lease==null) return;
        try {
            leases.fenced(lease,now,() -> {
                if(database.jdbc().queryForObject("select count(*) from ops_alert_rule",Long.class)>0) return null;
                addDefault("交互API技术错误","http.errorRate",5,2,5,2,100);
                addDefault("交互API慢响应","http.p95Ms",3000,2000,5,3,100);
                addDefault("AI技术失败","ai.technicalFailureRate",20,10,10,2,10);
                addDefault("AI业务解析失败","ai.businessFailureRate",20,10,10,2,10);
                addDefault("可运行队列等待","queue.oldestRunnableSeconds",120,120,1,2,0);
                addDefault("执行租约过期","execution.expiredLeases",1,1,1,2,0);
                addDefault("后台调度停滞","jobs.stalled",1,1,1,2,0);
                addDefault("数据库连接等待","db.pending",1,1,1,2,0);
                addDefault("JVM堆压力","jvm.heapPercent",85,75,1,3,0);
                addDefault("终态任务结算滞留","settlement.overdue",1,1,1,2,0);
                addDefault("监控自身故障","monitoring.faults",1,1,1,2,0);
                return null;
            });
            defaultsReady=true;
        } finally { leases.release(lease,now); }
    }
    private void addDefault(String name,String metric,double threshold,double recovery,int window,int sustained,long minimum) {
        var rule=new LinkedHashMap<String,Object>();
        rule.put("name",name); rule.put("metric",metric); rule.put("operator","GTE");
        rule.put("thresholdValue",threshold); rule.put("recoveryThreshold",recovery);
        rule.put("windowMinutes",window); rule.put("sustainedMinutes",sustained); rule.put("minimumSamples",minimum);
        rule.put("description","初始建议规则，可按实际流量和业务阶段编辑；恢复需要连续两个有效健康分钟窗口。");
        saveRule(null,rule);
    }

    public List<Map<String,Object>> rules() {
        return database.jdbc().queryForList("select "+RULE_COLUMNS+" from ops_alert_rule order by id limit 100")
            .stream().map(OperationsAlertService::camel).toList();
    }

    public Map<String,Object> saveRule(Long id,Map<String,Object> body) {
        String name=text(body.getOrDefault("name",""),160);
        String metric=String.valueOf(body.get("metric"));
        String severity=String.valueOf(body.getOrDefault("severity","WARNING"));
        String operator=String.valueOf(body.getOrDefault("operator","GTE"));
        if(name.isBlank() || !METRICS.contains(metric) || !Set.of("WARNING","CRITICAL").contains(severity)
            || !Set.of("GT","GTE","LT","LTE").contains(operator)) throw invalid("规则名称、指标、级别或比较符无效");
        double threshold=number(body.get("thresholdValue"));
        double recovery=number(body.getOrDefault("recoveryThreshold",threshold));
        if((operator.startsWith("G") && recovery>threshold) || (operator.startsWith("L") && recovery<threshold))
            throw invalid("恢复阈值必须位于健康方向");
        int window=(int)integer(body,"windowMinutes",5,1,60);
        int sustained=(int)integer(body,"sustainedMinutes",2,1,1440);
        long minimum=integer(body,"minimumSamples",0,0,1_000_000_000);
        int cooldown=(int)integer(body,"cooldownMinutes",30,1,1440);
        String dimensions=dimensions(metric,body.getOrDefault("dimensionsJson","{}"));
        boolean enabled=flag(body,"enabled",true), notify=flag(body,"notify",true);
        Instant now=clock.instant();
        return database.transactions().execute(tx -> {
            Long ruleId=id;
            if(id==null) {
                // The configuration row serializes concurrent creation and keeps evaluation bounded.
                database.jdbc().queryForList("select setting_key from ops_runtime_setting where setting_key='config' for update");
                if(database.jdbc().queryForObject("select count(*) from ops_alert_rule",Integer.class)>=100) throw invalid("最多100条告警规则");
                var keys=new GeneratedKeyHolder();
                database.jdbc().update(connection -> {
                    var statement=connection.prepareStatement("insert into ops_alert_rule(name,description,enabled,severity,metric,operator,threshold_value,recovery_threshold,window_minutes,sustained_minutes,minimum_samples,cooldown_minutes,dimensions_json,notify,version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)",new String[]{"id"});
                    Object[] values={name,text(body.get("description"),1000),enabled,severity,metric,operator,threshold,recovery,window,sustained,minimum,cooldown,dimensions,notify,ts(now),ts(now)};
                    for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
                    return statement;
                },keys);
                ruleId=Objects.requireNonNull(keys.getKey()).longValue();
            } else {
                long version=integer(body,"version",-1,0,Long.MAX_VALUE);
                var existing=database.jdbc().queryForList("select metric,operator,dimensions_json from ops_alert_rule where id=? for update",id);
                if(!existing.isEmpty() && (!metric.equals(existing.get(0).get("metric")) || !operator.equals(existing.get(0).get("operator"))
                    || !dimensions.equals(existing.get(0).get("dimensions_json")))) {
                    long active=database.jdbc().queryForObject("select count(*) from ops_alert_state where rule_id=? and active_event_id is not null",Long.class,id);
                    if(active>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"该规则有未恢复事件，暂不能更换指标、比较符或范围；可新建规则");
                }
                int changed=database.jdbc().update("update ops_alert_rule set name=?,description=?,enabled=?,severity=?,metric=?,operator=?,threshold_value=?,recovery_threshold=?,window_minutes=?,sustained_minutes=?,minimum_samples=?,cooldown_minutes=?,dimensions_json=?,notify=?,version=version+1,updated_at=? where id=? and version=?",
                    name,text(body.get("description"),1000),enabled,severity,metric,operator,threshold,recovery,window,sustained,minimum,cooldown,dimensions,notify,ts(now),id,version);
                if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"告警规则已修改或不存在，请刷新后重试");
            }
            return camel(database.jdbc().queryForMap("select "+RULE_COLUMNS+" from ops_alert_rule where id=?",ruleId));
        });
    }

    public void evaluate(Instant now) {
        initializeDefaults();
        Instant end=now.minusSeconds(10).truncatedTo(ChronoUnit.MINUTES);
        long started=System.nanoTime();
        var lease=leases.acquire("alerts-evaluate",settings.instanceId(),now,Duration.ofSeconds(25));
        if(lease==null) return;
        try {
            for(var rule:database.jdbc().queryForList("select "+RULE_COLUMNS+" from ops_alert_rule where enabled=true order by id limit 100")) {
                var sample=source.sample(str(rule,"metric"),str(rule,"dimensions_json"),num(rule,"window_minutes").intValue(),end);
                Instant publicationAt=now.plusNanos(System.nanoTime()-started);
                leases.fenced(lease,publicationAt,() -> { advance(rule,sample,end,now); return null; });
            }
        } finally { leases.release(lease,now); }
    }

    private void advance(Map<String,Object> rule,OperationsMetricSource.Sample sample,Instant end,Instant now) {
        long ruleId=num(rule,"id").longValue(), ruleVersion=num(rule,"version").longValue();
        // Recheck the rule under lock after the potentially slow metric query.
        var current=database.jdbc().queryForMap("select version,enabled from ops_alert_rule where id=? for update",ruleId);
        if(num(current,"version").longValue()!=ruleVersion || !Boolean.TRUE.equals(current.get("enabled"))) return;
        String key="rule:"+ruleId;
        var states=database.jdbc().queryForList("select state_key,rule_version,status,breached_since,last_window_at,healthy_windows,active_event_id,last_notified_at from ops_alert_state where state_key=? for update",key);
        Map<String,Object> state;
        if(states.isEmpty()) {
            database.jdbc().update("insert into ops_alert_state(state_key,rule_id,rule_version,status,healthy_windows,version,updated_at) values(?,?,?,'NORMAL',0,0,?)",key,ruleId,ruleVersion,ts(now));
            state=new HashMap<>();
        } else state=states.get(0);
        Instant previous=instant(state.get("last_window_at"));
        if(previous!=null && !end.isAfter(previous)) return;
        boolean consecutive=previous!=null && previous.plusSeconds(60).equals(end)
            && numOrZero(state,"rule_version")==ruleVersion;
        Instant breached=consecutive ? instant(state.get("breached_since")) : null;
        int healthy=consecutive ? (int)numOrZero(state,"healthy_windows") : 0;
        Long active=state.get("active_event_id")==null?null:((Number)state.get("active_event_id")).longValue();
        Instant notified=instant(state.get("last_notified_at"));
        String status;
        boolean valid=sample!=null && sample.complete() && sample.value()!=null && Double.isFinite(sample.value())
            && sample.samples()>=num(rule,"minimum_samples").longValue();
        if(!valid) { status="UNKNOWN"; breached=null; healthy=0; }
        else if(compare(sample.value(),str(rule,"operator"),num(rule,"threshold_value").doubleValue())) {
            healthy=0;
            if(breached==null) breached=end;
            if(active==null && Duration.between(breached,end).toMinutes()+1>=num(rule,"sustained_minutes").intValue()) {
                String eventKey=key+":"+end.getEpochSecond();
                database.jdbc().update("insert into ops_alert_event(event_key,rule_id,rule_name,severity,status,dimensions_json,metric_value,threshold_value,sample_count,fired_at,updated_at) values(?,?,?,?,'FIRING',?,?,?,?,?,?)",
                    eventKey,ruleId,str(rule,"name"),str(rule,"severity"),str(rule,"dimensions_json"),sample.value(),num(rule,"threshold_value"),sample.samples(),ts(now),ts(now));
                active=database.jdbc().queryForObject("select id from ops_alert_event where event_key=?",Long.class,eventKey);
            }
            status=active==null?"PENDING":"FIRING";
            if(active!=null) {
                database.jdbc().update("update ops_alert_event set updated_at=? where id=?",ts(now),active);
                if(notify(rule,active,notified,end,now,sample)) notified=now;
            }
        } else {
            breached=null;
            boolean recovered=str(rule,"operator").startsWith("G") ? sample.value()<num(rule,"recovery_threshold").doubleValue()
                : sample.value()>num(rule,"recovery_threshold").doubleValue();
            healthy=recovered?healthy+1:0;
            status=active==null?"NORMAL":"FIRING";
            if(active!=null && healthy>=2) {
                database.jdbc().update("update ops_alert_event set status='RESOLVED',resolved_at=?,updated_at=? where id=? and status='FIRING'",ts(now),ts(now),active);
                database.jdbc().update("insert into ops_alert_action(event_id,action,reason,created_at) values(?,'RESOLVED',?,?)",active,"连续2个有效健康窗口；恢复指标="+sample.value()+"，样本="+sample.samples(),ts(now));
                if(Boolean.TRUE.equals(rule.get("notify")) && settings.notificationsEnabled(now) && !silenced(active,now))
                    enqueue(active,"RESOLVED","event:"+active+":RESOLVED:feishu",message(active,"RESOLVED",rule,sample),now);
                active=null; notified=null; status="RESOLVED";
            }
        }
        database.jdbc().update("update ops_alert_state set rule_version=?,status=?,breached_since=?,last_window_at=?,healthy_windows=?,active_event_id=?,last_notified_at=?,version=version+1,updated_at=? where state_key=?",
            ruleVersion,status,ts(breached),ts(end),healthy,active,ts(notified),ts(now),key);
    }

    private boolean notify(Map<String,Object> rule,long event,Instant last,Instant end,Instant now,OperationsMetricSource.Sample sample) {
        if(!Boolean.TRUE.equals(rule.get("notify")) || !settings.notificationsEnabled(now) || silenced(event,now)) return false;
        if(last!=null && last.plusSeconds(num(rule,"cooldown_minutes").longValue()*60).isAfter(now)) return false;
        long recent=database.jdbc().queryForObject("select count(*) from ops_notification_outbox n join ops_alert_event e on e.id=n.event_id where e.rule_id=? and n.transition_type in ('FIRING','REMINDER') and n.created_at>?",
            Long.class,num(rule,"id"),ts(now.minusSeconds(3600)));
        if(recent>=2) return false;
        String transition=last==null?"FIRING":"REMINDER";
        String key="event:"+event+":"+transition+(last==null?"":":"+end.getEpochSecond())+":feishu";
        enqueue(event,transition,key,message(event,transition,rule,sample),now);
        return true;
    }
    private boolean silenced(long event,Instant now) {
        Instant until=instant(database.jdbc().queryForMap("select silence_until from ops_alert_event where id=?",event).get("silence_until"));
        return until!=null && until.isAfter(now);
    }
    private String message(long event,String transition,Map<String,Object> rule,OperationsMetricSource.Sample sample) {
        return "[OPS-"+event+"/"+transition+"] "+str(rule,"severity")+" "+str(rule,"name")
            +"\n指标："+str(rule,"metric")+"；值="+sample.value()+"；阈值="+rule.get("threshold_value")
            +"；样本="+sample.samples()+"；窗口="+rule.get("window_minutes")+"分钟\n范围："+str(rule,"dimensions_json");
    }
    void enqueue(Long event,String transition,String key,String message,Instant now) {
        database.jdbc().update("insert into ops_notification_outbox(notification_key,event_id,transition_type,message,status,attempts,next_attempt_at,created_at,updated_at) values(?,?,?,?,'PENDING',0,?,?,?)",
            key,event,transition,message,ts(now),ts(now),ts(now));
    }

    public Map<String,Object> acknowledge(long event,Long actor,String reason) {
        return action(event,actor,"ACKNOWLEDGE",reason,null);
    }
    public Map<String,Object> silence(long event,Long actor,Instant until,String reason) {
        Instant now=clock.instant();
        if(until==null || !until.isAfter(now) || until.isAfter(now.plus(30,ChronoUnit.DAYS))) throw invalid("静默期限必须在未来30天内");
        if(reason==null || reason.isBlank()) throw invalid("静默原因不能为空");
        return action(event,actor,"SILENCE",reason,until);
    }
    private Map<String,Object> action(long event,Long actor,String action,String reason,Instant until) {
        Instant now=clock.instant();
        return database.transactions().execute(tx -> {
            var rows=database.jdbc().queryForList("select id,status from ops_alert_event where id=? for update",event);
            if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"告警事件不存在");
            if(!"FIRING".equals(rows.get(0).get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"告警事件已恢复");
            String clean=text(reason,1000);
            if("ACKNOWLEDGE".equals(action)) database.jdbc().update("update ops_alert_event set acknowledged_at=?,acknowledged_by=?,updated_at=? where id=?",ts(now),actor,ts(now),event);
            else database.jdbc().update("update ops_alert_event set silence_until=?,silence_reason=?,updated_at=? where id=?",ts(until),clean,ts(now),event);
            database.jdbc().update("insert into ops_alert_action(event_id,action,actor_id,reason,until_at,created_at) values(?,?,?,?,?,?)",event,action,actor,clean,ts(until),ts(now));
            return camel(database.jdbc().queryForMap("select id,status,acknowledged_at,acknowledged_by,silence_until,silence_reason,updated_at from ops_alert_event where id=?",event));
        });
    }

    private String dimensions(String metric,Object value) {
        try {
            if(value instanceof String string && string.length()>2000) throw invalid("维度过长");
            if(value instanceof Map<?,?> map && map.size()>5) throw invalid("维度过多");
            Map<String,Object> scope=value instanceof Map<?,?> map ? json.convertValue(map,new TypeReference<TreeMap<String,Object>>(){})
                : json.readValue(String.valueOf(value),new TypeReference<TreeMap<String,Object>>(){});
            if(scope==null) throw invalid("维度必须为对象");
            Set<String> allowed=metric.equals("execution.phaseAgeSeconds")?Set.of("tenantId","projectId","modelId","scene","phase")
                :metric.startsWith("http.")?Set.of("route","method","instanceId")
                : metric.startsWith("ai.")?Set.of("tenantId","projectId","providerId","modelId","scene")
                : metric.startsWith("queue.") || metric.startsWith("execution.") || metric.startsWith("settlement.")?Set.of("tenantId","projectId","modelId","scene")
                :Set.of("instanceId");
            if(metric.equals("execution.phaseAgeSeconds") && !scope.containsKey("phase"))throw invalid("阶段耗时规则必须指定phase，不能跨不同阶段共用超时阈值");
            for(var entry:scope.entrySet()) {
                if(!allowed.contains(entry.getKey())) throw invalid("指标不支持该维度："+entry.getKey());
                if(entry.getKey().endsWith("Id") && !entry.getKey().equals("instanceId")) {
                    double numeric=number(entry.getValue());
                    if(numeric<1 || numeric!=Math.rint(numeric) || numeric>Long.MAX_VALUE) throw invalid("维度ID无效");
                    entry.setValue((long)numeric);
                } else {
                    if(!(entry.getValue() instanceof String text) || text.isBlank() || text.length()>128) throw invalid("维度值无效");
                    entry.setValue(text(entry.getValue(),128));
                }
            }
            String result=json.writeValueAsString(scope);
            if(result.length()>2000) throw invalid("维度过长");
            return result;
        } catch(BusinessException exception) { throw exception; }
        catch(Exception exception) { throw invalid("维度JSON无效"); }
    }
    private static boolean compare(double value,String operator,double threshold) {
        return switch(operator) { case "GT" -> value>threshold; case "GTE" -> value>=threshold;
            case "LT" -> value<threshold; case "LTE" -> value<=threshold; default -> false; };
    }
    private static boolean flag(Map<String,Object> body,String key,boolean fallback) {
        Object value=body.getOrDefault(key,fallback);
        if(!(value instanceof Boolean flag)) throw invalid(key+"必须为布尔值");
        return flag;
    }
    private static double number(Object value) {
        if(!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw invalid("指标阈值必须为有限数值");
        return number.doubleValue();
    }
    private static long integer(Map<String,Object> body,String key,long fallback,long minimum,long maximum) {
        double value=number(body.getOrDefault(key,fallback));
        if(value!=Math.rint(value) || value<minimum || value>maximum) throw invalid(key+"超出允许范围");
        return (long)value;
    }
    private static String text(Object value,int limit) {
        String result=new MonitoringEvent(null,"","","",value==null?"":String.valueOf(value),"",null,null,null,null).sanitized().message();
        return result.substring(0,Math.min(result.length(),limit));
    }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR,message); }
    static Timestamp ts(Instant value) { return value==null?null:Timestamp.from(value); }
    static Instant instant(Object value) { return OperationsTime.utc(value); }
    private static Number num(Map<String,Object> row,String key) { return (Number)row.get(key); }
    private static long numOrZero(Map<String,Object> row,String key) { return row.get(key)==null?0:num(row,key).longValue(); }
    private static String str(Map<String,Object> row,String key) { return String.valueOf(row.get(key)); }
    static Map<String,Object> camel(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        row.forEach((key,value) -> {
            StringBuilder name=new StringBuilder(); boolean uppercase=false;
            for(char c:key.toLowerCase(Locale.ROOT).toCharArray()) {
                if(c=='_') uppercase=true;
                else { name.append(uppercase?Character.toUpperCase(c):c); uppercase=false; }
            }
            result.put(name.toString(),value instanceof Timestamp || value instanceof java.time.LocalDateTime?OperationsTime.utc(value):value);
        });
        return result;
    }
}
