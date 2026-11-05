package com.antshorttv.operations;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operations.telemetry.LatencyHistogram;
import com.antshorttv.operations.telemetry.OperationsTelemetry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Fixed SQL projections and bounded time/ID queries; never reads business prompt/result bodies. */
@Service
public class OperationsQueryService implements OperationsMetricSource {
    private final OperationsDatabase database;
    private final OperationsSettingsService settings;
    private final ObjectMapper json;
    private OperationsTelemetry telemetry;
    public OperationsQueryService(OperationsDatabase database,OperationsSettingsService settings,ObjectMapper json) {this.database=database;this.settings=settings;this.json=json;}
    @Autowired void telemetry(OperationsTelemetry telemetry) { this.telemetry=telemetry; }
    public Map<String,Object> snapshot(OperationsQuery query) {
        var result=new LinkedHashMap<String,Object>();var failures=new ArrayList<String>();var statuses=new LinkedHashMap<String,Object>();
        result.put("from",query.from());result.put("to",query.to());result.put("generatedAt",Instant.now());
        var http=part("HTTP",failures,()->httpSummary(query,true),new LinkedHashMap<>());
        result.put("overview",http);
        result.put("trend",part("HTTP趋势",failures,()->trend(query),List.of()));
        result.put("resources",part("资源",failures,()->resources(query),List.of()));
        result.put("queue",part("队列",failures,()->queue(query),Map.of()));
        result.put("ai",part("AI",failures,()->aiSummary(query),Map.of()));
        var pipeline=telemetry==null?Map.<String,Object>of():telemetry.pipelineSnapshot();result.put("pipeline",pipeline);
        result.put("activeAlerts",part("告警",failures,()->database.jdbc().queryForObject("select count(*) from ops_alert_event where status='FIRING'",Long.class),null));
        var config=part("设置",failures,settings::current,Map.<String,Object>of());
        for(var entry:Map.of("overview","HTTP","trend","HTTP趋势","resources","资源","queue","队列","ai","AI","activeAlerts","告警","settings","设置").entrySet()){
            boolean failed=failures.contains(entry.getValue()+"暂不可用");statuses.put(entry.getKey(),map("status",failed?"ERROR":"OK","observedAt",failed?null:Instant.now()));
        }
        result.put("sourceStatus",statuses);
        result.put("externalProbeConfigured",Boolean.TRUE.equals(config.get("externalProbeConfigured")));
        String granularity=granularity(query);Instant first=null,last=null;
        try {
            var bounds=database.jdbc().queryForList("select min(bucket_at) first_at,max(bucket_at) last_at from ops_metric_bucket where family='RESOURCE' and granularity=? and bucket_at>=? and bucket_at<?",granularity,ts(query.from()),ts(query.to()));
            if(!bounds.isEmpty()){first=instant(bounds.get(0).get("first_at"));last=instant(bounds.get(0).get("last_at"));}
        }catch(org.springframework.dao.DataAccessException e){failures.add("覆盖信息不可用");}
        if(!query.dimensions().isEmpty())failures.add("HTTP和资源仅支持实例筛选；队列不支持服务商筛选。各区块明确独立范围。");
        var coverage=map("granularity",granularity,"firstAt",first,"lastAt",last,"complete",false,
            "reasons",List.of("异步采集可能存在空窗；执行历史仅保留成功采集版本；分位数为直方图近似值。"));
        result.put("coverage",coverage);
        boolean data=number(http.get("requestCount"))>0 || result.get("ai") instanceof Map<?,?> ai&&number(ai.get("calls"))>0;
        if(last!=null&&query.to().isAfter(Instant.now().minusSeconds(120))&&last.isBefore(Instant.now().minusSeconds(120))) failures.add("资源采样已过期");
        if(Boolean.FALSE.equals(pipeline.get("telemetryEnabled")))failures.add("监控采集已关闭");
        if(pipeline.get("lastFailure")!=null)failures.add("监控持久化或对账失败");
        if(query.to().isAfter(Instant.now().minusSeconds(120))){
            Instant closed=Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MINUTES);
            var coverageService=new OperationsCoverageService(database,json);
            Map<String,Object> instance=query.dimensions().containsKey("instanceId")?Map.of("instanceId",query.dimensions().get("instanceId")):Map.of();
            if(number(http.get("requestCount"))>0&&!coverageService.complete("http.errorRate",instance,1,closed))failures.add("接口窗口尚未完成持久化或存在采集缺口");
            if(result.get("ai") instanceof Map<?,?> ai&&number(ai.get("calls"))>0&&!coverageService.complete("ai.technicalFailureRate",Map.of(),1,closed))failures.add("AI窗口对账尚未完成或存在采集缺口");
        }
        String health=!data?"NO_DATA":!failures.isEmpty()?"WARNING":number(result.get("activeAlerts"))>0?"ERROR":"NORMAL";
        result.put("health",map("status",health,"reasons",failures));return result;
    }
    private <T>T part(String label,List<String> failures,Supplier<T> source,T fallback){try{return source.get();}catch(RuntimeException e){failures.add(label+"暂不可用");return fallback;}}
    private String granularity(OperationsQuery query) {
        int retained=((Number)settings.current().get("minuteRetentionDays")).intValue();
        return query.from().isBefore(Instant.now().minus(retained,ChronoUnit.DAYS))||Duration.between(query.from(),query.to()).toDays()>=2?"HOUR":"MINUTE";
    }
    private List<Map<String,Object>> buckets(OperationsQuery query,String family,boolean global) {
        Set<String> allowed=family.equals("HTTP")?Set.of("instanceId","route","method"):family.equals("RESOURCE")?Set.of("instanceId"):Set.of("tenantId","projectId","providerId","modelId","scene");
        if(!allowed.containsAll(query.dimensions().keySet()))invalid("此指标不支持所选筛选维度");
        var args=new ArrayList<Object>(List.of(family,granularity(query),ts(query.from()),ts(query.to())));
        StringBuilder sql=new StringBuilder("select bucket_at,instance_id,dimensions_json,count_value,error_count,business_count,sum_ms,max_ms,histogram_json,values_json,updated_at from ops_metric_bucket where family=? and granularity=? and bucket_at>=? and bucket_at<?");
        if(query.dimensions().containsKey("instanceId")){sql.append(" and instance_id=?");args.add(query.dimensions().get("instanceId"));}
        // The canonical scope index is stable across collectors; SQL filters prevent full route scans on overview.
        if(global){sql.append(" and dimensions_key=?");args.add(globalKey());}
        else if(!family.equals("RESOURCE"))sql.append(" and dimensions_json<>'{\"scope\":\"ALL\"}'");
        if(family.equals("AI"))appendFilters(sql,args,query.dimensions(),allowed,"");
        if(family.equals("HTTP"))for(String key:List.of("route","method"))if(query.dimensions().containsKey(key)){sql.append(" and ").append(key).append("=?");args.add(query.dimensions().get(key));}
        sql.append(" order by bucket_at limit 20001");
        var rows=database.jdbc().queryForList(sql.toString(),args.toArray());
        if(rows.size()>20000)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"指标维度过多，请缩小时间或筛选范围。");
        return rows;
    }
    private Map<String,Object> httpSummary(OperationsQuery query,boolean global) {
        if(query.dimensions().keySet().stream().anyMatch(k->!Set.of("instanceId","route","method").contains(k)))return map("requestCount",null,"scope","INSTANCE_ONLY","reason","HTTP不采集团队/项目/模型标签");
        if(query.dimensions().containsKey("route")||query.dimensions().containsKey("method"))global=false;
        var aggregate=new Aggregate();for(var row:buckets(query,"HTTP",global))aggregate.add(row);
        var result=aggregate.values();result.put("requestCount",aggregate.count);result.put("technicalErrors",aggregate.errors);result.put("businessRejections",aggregate.business);
        result.put("errorRate",aggregate.count==0?null:100d*aggregate.errors/aggregate.count);
        long covered=coveredSeconds(query);result.put("qps",covered==0?null:(double)aggregate.count/covered);
        result.put("coveredSeconds",covered);result.put("qpsDenominator","OBSERVED_RESOURCE_BUCKET_SECONDS");
        result.put("scope","INSTANCE_ONLY");return result;
    }
    public Map<String,Object> requests(OperationsQuery query) {
        var routes=new TreeMap<String,Aggregate>();
        for(var row:buckets(query,"HTTP",false)) {
            var dimensions=decode(row.get("dimensions_json"));String key=dimensions.get("method")+" "+dimensions.get("route");
            routes.computeIfAbsent(key,k->new Aggregate()).add(row);
        }
        var items=new ArrayList<Map<String,Object>>();
        for(var entry:routes.entrySet()) {
            var aggregate=entry.getValue();var item=aggregate.values();int space=entry.getKey().indexOf(' ');
            item.put("method",entry.getKey().substring(0,space));item.put("route",entry.getKey().substring(space+1));
            item.put("requests",aggregate.count);item.put("errors",aggregate.errors);item.put("businessRejections",aggregate.business);items.add(item);
        }
        items.sort(Comparator.comparingDouble((Map<String,Object> m)->m.get("p95Ms") instanceof Number n?n.doubleValue():0).reversed());
        // Route ranking uses bounded route offset cursors; detail sources below use time+ID cursors.
        int offset=0;try{if(query.cursor()!=null)offset=Integer.parseInt(query.cursor());}catch(NumberFormatException e){invalid("分页游标不正确");}
        if(offset<0||offset>items.size())invalid("分页游标已过期");
        int end=Math.min(items.size(),offset+query.pageSize());return map("items",items.subList(offset,end),"total",items.size(),"nextCursor",end<items.size()?String.valueOf(end):null);
    }
    private List<Map<String,Object>> trend(OperationsQuery query) {
        if(query.dimensions().keySet().stream().anyMatch(k->!k.equals("instanceId")))return List.of();
        var times=new TreeMap<Instant,Aggregate>();
        for(var row:buckets(query,"HTTP",true))times.computeIfAbsent(instant(row.get("bucket_at")),k->new Aggregate()).add(row);
        return times.entrySet().stream().<Map<String,Object>>map(e->map("at",e.getKey(),"requests",e.getValue().count,"errors",e.getValue().errors,"p95Ms",e.getValue().histogram.percentile(.95))).toList();
    }
    private List<Map<String,Object>> resources(OperationsQuery query) {
        var resourceQuery=new OperationsQuery(query.from(),query.to(),100,null,query.dimensions().containsKey("instanceId")?Map.of("instanceId",query.dimensions().get("instanceId")):Map.of());
        var latest=new LinkedHashMap<String,Map<String,Object>>();
        for(var row:buckets(resourceQuery,"RESOURCE",false)){var value=decode(row.get("values_json"));value.put("instanceId",row.get("instance_id"));value.put("at",instant(row.get("bucket_at")));latest.put(String.valueOf(row.get("instance_id")),value);}
        return new ArrayList<>(latest.values());
    }
    public Map<String,Object> aiSummary(OperationsQuery query) {
        if(query.dimensions().containsKey("instanceId"))return map("calls",null,"scope","GLOBAL_PROJECTION","reason","AI投影不保留实例维度");
        var aggregate=new Aggregate();for(var row:buckets(query,"AI",query.dimensions().isEmpty()))aggregate.add(row);
        long tokens=aggregate.extra.getOrDefault("tokenSamples",0L);
        return map("calls",aggregate.count,"technicalFailures",aggregate.errors,"businessFailures",aggregate.business,
            "rateLimited",aggregate.extra.getOrDefault("rateLimited",0L),"timeouts",aggregate.extra.getOrDefault("timeouts",0L),
            "successRate",aggregate.count==0?null:100d*(aggregate.count-aggregate.errors)/aggregate.count,
            "promptTokens",tokens==0?null:aggregate.extra.getOrDefault("promptTokens",0L),"completionTokens",tokens==0?null:aggregate.extra.getOrDefault("completionTokens",0L),
            "tokenCoverage",aggregate.count==0?null:100d*tokens/aggregate.count,"scope","CALLS","projectionCoverage","PARTIAL");
    }
    private Map<String,Object> queue(OperationsQuery query) {
        if(query.dimensions().containsKey("providerId")||query.dimensions().containsKey("instanceId"))return map("pending",null,"scope","CURRENT_EXECUTIONS","reason","任务队列不支持实例/服务商维度");
        var args=new ArrayList<Object>();StringBuilder where=new StringBuilder(" where 1=1");
        appendFilters(where,args,query.dimensions(),Set.of("tenantId","projectId","modelId","scene"),"o.");
        var rows=database.jdbc().queryForList("select o.status,o.next_run_at,o.created_at,o.claim_expires_at,o.completed_at,o.point_settlement_status,o.observed_at from ops_execution_observation o join (select execution_id,max(execution_version) version from ops_execution_observation group by execution_id) latest on latest.execution_id=o.execution_id and latest.version=o.execution_version"+where+" and (o.status in ('PENDING','RUNNING') or o.point_settlement_status='RESERVED') limit 10001",args.toArray());
        if(rows.size()>10000)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"队列观察范围过大，请缩小团队范围");
        Instant now=Instant.now();long pending=0,runnable=0,retrying=0,running=0,expired=0,settlement=0;double oldest=0;
        for(var row:rows){String status=String.valueOf(row.get("status"));
            if(status.equals("PENDING")){pending++;Instant next=instant(row.get("next_run_at"));if(next!=null&&next.isAfter(now))retrying++;else{runnable++;Instant at=next==null?instant(row.get("created_at")):next;if(at!=null)oldest=Math.max(oldest,Duration.between(at,now).getSeconds());}}
            if(status.equals("RUNNING")){running++;Instant lease=instant(row.get("claim_expires_at"));if(lease!=null&&lease.isBefore(now.minusSeconds(120)))expired++;}
            // RESERVED is positive reservation evidence; PENDING alone does not establish a settlement obligation.
            Instant completed=instant(row.get("completed_at"));if(Set.of("SUCCEEDED","FAILED","CANCELED","TIMED_OUT").contains(status)&&"RESERVED".equals(row.get("point_settlement_status"))&&completed!=null&&completed.isBefore(now.minusSeconds(300)))settlement++;
        }
        return map("pending",pending,"runnable",runnable,"retrying",retrying,"running",running,"oldestRunnableSeconds",oldest,"expiredLeases",expired,"settlementPending",settlement,"scope","CURRENT_EXECUTIONS","sampleCount",rows.size(),"projectionFresh",projectionFresh(query.to()));
    }
    private record Source(String table,String id,String time,String fields,Set<String> dimensions) {}
    private static final Map<String,Source> SOURCES=Map.of(
        "events",new Source("ops_event","id","occurred_at","id,occurred_at,source,code,location,message,fingerprint,user_id,tenant_id,project_id,execution_id,request_id,trace_id,trusted_context,duration_ms,sql_count,sql_ms",Set.of("tenantId","projectId")),
        "ai",new Source("ops_ai_call_projection","call_log_id","occurred_at","call_log_id,occurred_at,tenant_id,project_id,provider_id,model_id,scene,service_type,trace_id,execution_id,attempt_id,transport_outcome,business_outcome,status,error_code,duration_ms,prompt_tokens,completion_tokens,updated_at",Set.of("tenantId","projectId","providerId","modelId","scene")),
        "executions",new Source("ops_execution_observation","id","observed_at","id,execution_id,execution_version,tenant_id,project_id,model_id,scene,task_type,status,phase,created_at,started_at,completed_at,observed_at,next_run_at,heartbeat_at,claim_expires_at,point_settlement_status,error_code,error_message,trace_id",Set.of("tenantId","projectId","modelId","scene")),
        "jobs",new Source("ops_job_heartbeat","id","updated_at","id,instance_id,boot_id,job_code,enabled,schedule_type,interval_ms,schedule_expression,expected_at,started_at,finished_at,succeeded_at,error_at,duration_ms,consecutive_failures,error_message,updated_at",Set.of("instanceId")),
        "alerts",new Source("ops_alert_event","id","fired_at","id,event_key,rule_id,rule_name,severity,status,dimensions_json,metric_value,threshold_value,sample_count,fired_at,resolved_at,acknowledged_at,acknowledged_by,silence_until,silence_reason,updated_at",Set.of()),
        "notifications",new Source("ops_notification_outbox","id","created_at","id,notification_key,event_id,transition_type,status,attempts,next_attempt_at,delivered_at,last_error,created_at,updated_at",Set.of()),
        "actions",new Source("ops_alert_action","id","created_at","id,event_id,action,actor_id,reason,until_at,created_at",Set.of())
    );
    public Map<String,Object> list(String name,OperationsQuery query,Long parent) {
        Source source=SOURCES.get(name);if(source==null)invalid("未知监控数据源");
        StringBuilder where=new StringBuilder();var args=new ArrayList<Object>();
        if(name.equals("jobs"))where.append(" where not exists (select 1 from ops_job_heartbeat newer where newer.instance_id=ops_job_heartbeat.instance_id and newer.boot_id<>ops_job_heartbeat.boot_id and newer.updated_at>ops_job_heartbeat.updated_at)");
        else{where.append(" where ").append(source.time()).append(">=? and ").append(source.time()).append("<?");args.add(ts(query.from()));args.add(ts(query.to()));}
        appendFilters(where,args,query.dimensions(),source.dimensions(),"");
        if(name.equals("actions")){if(parent==null)invalid("缺少告警编号");where.append(" and event_id=?");args.add(parent);}
        Long total=database.jdbc().queryForObject("select count(*) from "+source.table()+where,Long.class,args.toArray());
        List<Map<String,Object>> groups=name.equals("events")?database.jdbc().queryForList("select fingerprint,source,code,min(location) location,count(*) count,max(occurred_at) last_at from ops_event"+where+" group by fingerprint,source,code order by count(*) desc,max(occurred_at) desc limit 20",args.toArray()):List.of();
        if(query.cursor()!=null){var cursor=cursor(query.cursor());where.append(" and ("+source.time()+"<? or ("+source.time()+"=? and "+source.id()+"<?))");args.add(ts(cursor.at));args.add(ts(cursor.at));args.add(cursor.id);}
        var rows=database.jdbc().queryForList("select "+source.fields()+" from "+source.table()+where+" order by "+source.time()+" desc,"+source.id()+" desc limit ?",concat(args,query.pageSize()+1));
        String next=null;if(rows.size()>query.pageSize()){rows.remove(rows.size()-1);var last=rows.get(rows.size()-1);next=encodeCursor(instant(last.get(source.time())),((Number)last.get(source.id())).longValue());}
        var result=map("items",rows.stream().map(this::camel).toList(),"total",total,"nextCursor",next);
        if(name.equals("events"))result.put("groups",groups.stream().map(this::camel).toList());
        if(name.equals("jobs"))result.put("scope","CURRENT_JOB_REGISTRY");
        if(name.equals("alerts")){
            @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)result.get("items");
            if(!items.isEmpty()){
                var ids=items.stream().map(row->row.get("id")).toList();
                var states=database.jdbc().queryForList("select active_event_id,status,last_window_at from ops_alert_state where active_event_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",ids.toArray());
                for(var item:items)for(var state:states)if(Objects.equals(item.get("id"),state.get("active_event_id"))){item.put("evidenceStatus",state.get("status"));item.put("lastWindowAt",instant(state.get("last_window_at")));}
            }
        }
        if(name.equals("ai"))result.put("summary",aiSummary(query));return result;
    }
    public Map<String,Object> event(long id) {
        var rows=database.jdbc().queryForList("select id,occurred_at,source,code,location,message,stack,fingerprint,user_id,tenant_id,project_id,execution_id,request_id,trace_id,trusted_context,duration_ms,sql_count,sql_ms,attributes_json from ops_event where id=?",id);
        if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"事件不存在或详情已过保留期。");return camel(rows.get(0));
    }
    public Map<String,Object> execution(long executionId,OperationsQuery query){
        var common=new LinkedHashMap<>(query.dimensions());Object provider=common.remove("providerId");
        if(!Set.of("tenantId","projectId","modelId","scene").containsAll(common.keySet()))invalid("执行详情不支持此筛选维度");
        var parameters=new ArrayList<Object>(List.of(executionId));
        var where=new StringBuilder(" where execution_id=?");appendFilters(where,parameters,common,Set.of("tenantId","projectId","modelId","scene"),"");
        String columns=SOURCES.get("executions").fields();
        var versions=database.jdbc().queryForList("select "+columns+" from ops_execution_observation"+where+" order by execution_version desc limit 101",parameters.toArray());
        if(versions.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"执行版本不存在、详情已过保留期或不符合筛选范围。");
        // Attempts are read only, have an execution/time index and never include input/output summaries.
        var attemptArgs=new ArrayList<Object>(List.of(executionId,java.time.LocalDateTime.ofInstant(query.from(),java.time.ZoneId.systemDefault()),java.time.LocalDateTime.ofInstant(query.to(),java.time.ZoneId.systemDefault())));
        String attemptWhere=" where execution_id=? and started_at>=? and started_at<?";
        if(provider!=null){attemptWhere+=" and provider_id=?";attemptArgs.add(provider);}
        var attempts=database.jdbc().query("select id,execution_id,execution_version,phase,attempt_no,status,provider_id,model_id,transport_outcome,business_outcome,error_code,left(error_message,500) error_message,started_at,finished_at from ai_execution_attempt"+attemptWhere+" order by started_at desc,id desc limit 101",(rs,index)->{
            Map<String,Object> row=new LinkedHashMap<>();var metadata=rs.getMetaData();
            for(int i=1;i<=metadata.getColumnCount();i++){String column=metadata.getColumnLabel(i);Object value=Set.of("started_at","finished_at").contains(column)?rs.getObject(i,java.time.LocalDateTime.class):rs.getObject(i);
                if(value instanceof java.time.LocalDateTime wall)value=wall.atZone(java.time.ZoneId.systemDefault()).toInstant().toString();row.put(column,value);}
            return row;
        },attemptArgs.toArray());
        var callArgs=new ArrayList<Object>(List.of(executionId,ts(query.from()),ts(query.to())));String callWhere=" where execution_id=? and occurred_at>=? and occurred_at<?";
        if(provider!=null){callWhere+=" and provider_id=?";callArgs.add(provider);}
        var calls=database.jdbc().queryForList("select "+SOURCES.get("ai").fields()+" from ops_ai_call_projection"+callWhere+" order by occurred_at desc,call_log_id desc limit 101",callArgs.toArray());
        boolean truncated=versions.size()>100||attempts.size()>100||calls.size()>100;
        return map("executionId",executionId,"from",query.from(),"to",query.to(),"coverage","PARTIAL","versionScope","LATEST_RETAINED_CONTEXT","truncated",truncated,
            "versions",versions.stream().limit(100).map(this::camel).toList(),"attempts",attempts.stream().limit(100).map(this::camel).toList(),"calls",calls.stream().limit(100).map(this::camel).toList());
    }
    private void appendFilters(StringBuilder sql,List<Object> args,Map<String,Object> dimensions,Set<String> allowed,String prefix){
        if(!allowed.containsAll(dimensions.keySet()))invalid("此数据源不支持所选维度");
        for(var entry:dimensions.entrySet()){sql.append(" and ").append(prefix).append(snake(entry.getKey())).append("=?");args.add(entry.getValue());}
    }
    @Override public Sample sample(String metric,String dimensionsJson,int windowMinutes,Instant end) {
        try {
            if(windowMinutes<1||windowMinutes>1440)return new Sample(null,0,false);
            var dimensions=decode(dimensionsJson);var query=new OperationsQuery(end.minusSeconds(windowMinutes*60L),end,20,null,dimensions);
            boolean complete=new OperationsCoverageService(database,json).complete(metric,dimensions,windowMinutes,end);
            if(Set.of("http.errorRate","http.p95Ms").contains(metric)){var summary=httpSummary(query,true);long count=number(summary.get("requestCount"));return new Sample(numeric(summary.get(metric.equals("http.errorRate")?"errorRate":"p95Ms")),count,complete);}
            if(Set.of("ai.technicalFailureRate","ai.businessFailureRate").contains(metric)){var summary=aiSummary(query);long calls=number(summary.get("calls"));return new Sample(calls==0?null:100d*number(summary.get(metric.equals("ai.technicalFailureRate")?"technicalFailures":"businessFailures"))/calls,calls,complete&&projectionFresh(end));}
            if(metric.equals("execution.phaseAgeSeconds"))return phaseAge(query);
            if(Set.of("queue.oldestRunnableSeconds","execution.expiredLeases","settlement.overdue").contains(metric)){
                var queue=queue(query);String key=metric.startsWith("queue")?"oldestRunnableSeconds":metric.startsWith("execution")?"expiredLeases":"settlementPending";
                return new Sample(numeric(queue.get(key)),1,projectionFresh(end));
            }
            if(metric.equals("jobs.stalled")){Double value=stalled(end,dimensions);return new Sample(value,1,value!=null);}
            if(metric.equals("db.pending")||metric.equals("jvm.heapPercent")){
                double value=0;long count=0;for(var resource:resources(query)){
                    Double observed=metric.equals("db.pending")?numeric(resource.get("dbPending")):number(resource.get("heapMaxBytes"))>0?100d*number(resource.get("heapUsedBytes"))/number(resource.get("heapMaxBytes")):null;
                    if(observed!=null){value=Math.max(value,observed);count++;}
                }return new Sample(count==0?null:value,count,complete);
            }
            if(metric.equals("monitoring.faults")){
                if(dimensions.keySet().stream().anyMatch(k->!k.equals("instanceId")))return new Sample(null,0,false);
                var sample=new OperationsPipelineHealth(database,json).sample(latestPipelineResources(query),windowMinutes,end,true);
                var args=new ArrayList<Object>();String selected="";if(dimensions.containsKey("instanceId")){selected=" and h.instance_id=?";args.add(dimensions.get("instanceId"));}
                long failed=database.jdbc().queryForObject("select count(*) from ops_job_heartbeat h where h.enabled=true and h.job_code like 'monitoring.%' and h.consecutive_failures>0 and not exists (select 1 from ops_job_heartbeat newer where newer.instance_id=h.instance_id and newer.boot_id<>h.boot_id and newer.updated_at>h.updated_at)"+selected,Long.class,args.toArray());
                return failed>0?new Sample(failed+(sample.value()==null?0:sample.value()),Math.max(1,sample.samples()),true):sample;
            }
        }catch(RuntimeException ignored){return new Sample(null,0,false);}return new Sample(null,0,false);
    }
    private boolean projectionFresh(Instant end){
        var rows=database.jdbc().queryForList("select value_json,updated_at from ops_runtime_setting where setting_key='telemetry.executions.active'");
        if(rows.isEmpty())return false;Instant at=instant(rows.get(0).get("updated_at"));
        var state=decode(rows.get(0).get("value_json"));Object completed=state.get("completedAt");
        return at!=null&&!at.isBefore(end.minusSeconds(120))&&completed!=null&&!completed.toString().isBlank()&&!Instant.parse(completed.toString()).isBefore(end.minusSeconds(300));
    }
    private Sample phaseAge(OperationsQuery query){
        var dimensions=new LinkedHashMap<>(query.dimensions());Object phase=dimensions.remove("phase");
        if(!(phase instanceof String text)||text.isBlank()||text.length()>128||!Set.of("tenantId","projectId","modelId","scene").containsAll(dimensions.keySet()))return new Sample(null,0,false);
        var args=new ArrayList<Object>(List.of(phase,java.time.LocalDateTime.ofInstant(query.to(),java.time.ZoneId.systemDefault())));
        var where=new StringBuilder(" where o.status='RUNNING' and a.phase=? and a.status='STARTED' and a.finished_at is null and a.started_at<?");
        appendFilters(where,args,dimensions,Set.of("tenantId","projectId","modelId","scene"),"o.");
        var starts=database.jdbc().query("select a.started_at from ops_execution_observation o join (select execution_id,max(execution_version) version from ops_execution_observation group by execution_id) latest on latest.execution_id=o.execution_id and latest.version=o.execution_version join ai_execution_attempt a on a.execution_id=o.execution_id and a.execution_version=o.execution_version"+where+" limit 10001",(rs,index)->rs.getObject(1,java.time.LocalDateTime.class),args.toArray());
        if(starts.size()>10000)return new Sample(null,0,false);
        double oldest=0;for(var start:starts)oldest=Math.max(oldest,Duration.between(start.atZone(java.time.ZoneId.systemDefault()).toInstant(),query.to()).getSeconds());
        return new Sample(oldest,starts.size(),projectionFresh(query.to()));
    }
    private long coveredSeconds(OperationsQuery query){
        var args=new ArrayList<Object>(List.of(granularity(query),ts(query.from()),ts(query.to())));String sql="select count(distinct bucket_at) from ops_metric_bucket where family='RESOURCE' and granularity=? and bucket_at>=? and bucket_at<?";
        if(query.dimensions().containsKey("instanceId")){sql+=" and instance_id=?";args.add(query.dimensions().get("instanceId"));}
        long seconds=database.jdbc().queryForObject(sql,Long.class,args.toArray())*(granularity(query).equals("HOUR")?3600:60);
        return Math.min(seconds,Duration.between(query.from(),query.to()).getSeconds());
    }
    private List<Map<String,Object>> latestPipelineResources(OperationsQuery query){
        if(query.dimensions().keySet().stream().anyMatch(k->!k.equals("instanceId")))return List.of();
        var args=new ArrayList<Object>(List.of(ts(query.to().minusSeconds(86400)),ts(query.to())));
        String selected="";if(query.dimensions().containsKey("instanceId")){selected=" and instance_id=?";args.add(query.dimensions().get("instanceId"));}
        var rows=database.jdbc().queryForList("select m.instance_id,m.bucket_at,m.values_json from ops_metric_bucket m join (select instance_id,max(bucket_at) latest from ops_metric_bucket where family='RESOURCE' and granularity='MINUTE' and bucket_at>=? and bucket_at<?"+selected+" group by instance_id) recent on m.instance_id=recent.instance_id and m.bucket_at=recent.latest where m.family='RESOURCE' and m.granularity='MINUTE' limit 1001",args.toArray());
        if(rows.size()>1000)return List.of();
        return rows.stream().map(row->{var values=decode(row.get("values_json"));values.put("instanceId",row.get("instance_id"));values.put("at",instant(row.get("bucket_at")));return values;}).toList();
    }
    private Double stalled(Instant end,Map<String,Object> dimensions){
        if(dimensions.keySet().stream().anyMatch(k->!k.equals("instanceId")))return null;
        var args=new ArrayList<Object>();String sql="select h.expected_at,h.started_at,h.finished_at,h.interval_ms,h.schedule_type,h.updated_at from ops_job_heartbeat h where h.enabled=true and not exists (select 1 from ops_job_heartbeat newer where newer.instance_id=h.instance_id and newer.boot_id<>h.boot_id and newer.updated_at>h.updated_at)";
        if(dimensions.containsKey("instanceId")){sql+=" and h.instance_id=?";args.add(dimensions.get("instanceId"));}
        var rows=database.jdbc().queryForList(sql+" limit 1000",args.toArray());if(rows.isEmpty())return null;long stalled=0;
        for(var row:rows){Instant expected=instant(row.get("expected_at")),started=instant(row.get("started_at")),finished=instant(row.get("finished_at"));
            long grace=Math.max(60,3*number(row.get("interval_ms"))/1000);
            if(instant(row.get("updated_at")).isBefore(end.minusSeconds(120)))stalled++;
            else if(expected!=null&&expected.plusSeconds(grace).isBefore(end))stalled++;
            else if(started!=null&&(finished==null||finished.isBefore(started))&&started.plusSeconds(Math.max(1800,grace)).isBefore(end))stalled++;
        }return (double)stalled;
    }
    private class Aggregate {
        long count,errors,business;double max;final LatencyHistogram histogram=new LatencyHistogram();final Map<String,Long> extra=new HashMap<>();
        void add(Map<String,Object> row){count+=number(row.get("count_value"));errors+=number(row.get("error_count"));business+=number(row.get("business_count"));max=Math.max(max,number(row.get("max_ms")));
            try{if(row.get("histogram_json")!=null)histogram.merge(json.readValue(row.get("histogram_json").toString(),long[].class));}catch(Exception e){throw new IllegalStateException("Invalid histogram",e);}
            decode(row.get("values_json")).forEach((key,value)->{if(value instanceof Number n)extra.merge(key,n.longValue(),Long::sum);});
        }
        Map<String,Object> values(){var bins=new ArrayList<Map<String,Object>>();long[] counts=histogram.counts();for(int i=0;i<counts.length;i++)bins.add(map("upperBoundMs",i<LatencyHistogram.BOUNDS.length?LatencyHistogram.BOUNDS[i]:null,"count",counts[i]));
            return map("p50Ms",histogram.percentile(.5),"p95Ms",histogram.percentile(.95),"p99Ms",histogram.percentile(.99),"p99Overflow",histogram.exceedsBound(.99),"maxMs",count==0?null:max,"sqlCount",extra.getOrDefault("sqlCount",0L),"sqlMs",extra.getOrDefault("sqlMs",0L),"histogram",bins,"approximate",true,"samples",count);}
    }
    private Map<String,Object> decode(Object value){if(value==null)return new LinkedHashMap<>();try{return json.readValue(value.toString(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid operations JSON",e);}}
    private Map<String,Object> camel(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();row.forEach((key,value)->{StringBuilder name=new StringBuilder();boolean upper=false;for(char c:key.toCharArray()){if(c=='_'){upper=true;continue;}name.append(upper?Character.toUpperCase(c):Character.toLowerCase(c));upper=false;}
        if(value instanceof String text&&Set.of("message","stack","error_message","location","attributes_json","trace_id","request_id").contains(key)){
            var evidence=new com.antshorttv.monitoring.MonitoringEvent(Instant.EPOCH,"","",key.equals("location")?text:"",key.equals("stack")?"":text,key.equals("stack")?text:"",null,null,null,null).sanitized();
            value=key.equals("location")?evidence.location():key.equals("stack")?evidence.stack():evidence.message();
        }
        result.put(name.toString(),value instanceof Timestamp||value instanceof java.time.LocalDateTime?instant(value):value);});return result;}
    private record Cursor(Instant at,long id){}
    private Cursor cursor(String value){try{String[] parts=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8).split("\\|",2);return new Cursor(Instant.parse(parts[0]),Long.parseLong(parts[1]));}catch(RuntimeException e){invalid("分页游标不正确");return null;}}
    private String encodeCursor(Instant at,long id){return Base64.getUrlEncoder().withoutPadding().encodeToString((at+"|"+id).getBytes(StandardCharsets.UTF_8));}
    private static String globalKey(){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("{\"scope\":\"ALL\"}".getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String snake(String key){return key.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    private static Object[] concat(List<Object> args,Object value){var copy=new ArrayList<>(args);copy.add(value);return copy.toArray();}
    static Timestamp ts(Instant at){return Timestamp.from(at);}
    static Instant instant(Object value){if(value==null)return null;if(value instanceof Timestamp t)return t.toInstant();if(value instanceof java.time.LocalDateTime local)return local.toInstant(java.time.ZoneOffset.UTC);return Instant.parse(value.toString());}
    static long number(Object value){return value instanceof Number n?n.longValue():0;}
    static Double numeric(Object value){return value instanceof Number n?n.doubleValue():null;}
    static LinkedHashMap<String,Object> map(Object... entries){var map=new LinkedHashMap<String,Object>();for(int i=0;i<entries.length;i+=2)map.put(entries[i].toString(),entries[i+1]);return map;}
    private static void invalid(String message){throw new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
}
