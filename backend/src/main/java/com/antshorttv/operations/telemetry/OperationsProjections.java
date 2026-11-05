package com.antshorttv.operations.telemetry;

import com.antshorttv.operations.OperationsDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.antshorttv.operations.telemetry.OperationsTelemetry.*;

/** Lightweight allowlisted reads only; never reads source summaries, prompts or results. */
@Component
public class OperationsProjections {
    // Each page shares the one-second monitoring transaction, including per-row upserts.
    private static final int SOURCE_BATCH_SIZE=10;
    private static final Set<String> LOCAL_REJECTIONS=Set.of("VALIDATION_ERROR","TEAM_POINTS_INSUFFICIENT","UNAUTHORIZED","FORBIDDEN","PROJECT_ACCESS_DENIED","AI_MODEL_NOT_FOUND","AI_MODEL_DISABLED","AI_MODEL_BILLING_MISSING","AI_PROVIDER_NOT_FOUND","AI_PROVIDER_DISABLED","AI_EXECUTION_STATUS_INVALID","AI_VIDEO_CONCURRENCY_LIMIT_EXCEEDED");
    private static final String CALL_FIELDS="c.id,c.created_at,c.tenant_id,t.project_id,c.provider_id,c.model_id,c.business_scene,c.service_type,c.trace_id,c.execution_id,c.attempt_id,c.transport_outcome,c.business_outcome,c.status,c.duration_ms,c.prompt_tokens,c.completion_tokens,"+sourceErrorCode();
    private static String sourceErrorCode() {
        StringBuilder sql=new StringBuilder("case ");
        for(var code:com.antshorttv.common.ErrorCode.values())sql.append("when left(c.error_message,").append(code.name().length()+1).append(")='").append(code.name()).append(" ' then '").append(code.name()).append("' ");
        return sql.append("else null end error_code").toString();
    }
    private static final String EXEC_FIELDS="id,execution_version,tenant_id,project_id,coalesce(resolved_model_id,requested_model_id) model_id,scene,task_type,status,phase,created_at,updated_at,started_at,completed_at,next_run_at,heartbeat_at,claim_expires_at,point_settlement_status,error_code,left(error_message,500) error_message,trace_id";
    private final OperationsDatabase database;
    private final OperationsTelemetry telemetry;
    private final ObjectMapper mapper;
    private final Set<String> pendingAggregates=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Deque<CoverageRange> coverageCandidates=new ArrayDeque<>();
    public OperationsProjections(OperationsDatabase database,OperationsTelemetry telemetry,ObjectMapper mapper) { this.database=database;this.telemetry=telemetry;this.mapper=mapper; }

    public void projectCall(long id) {
        var rows=sourceRows("select "+CALL_FIELDS+" from ai_call_log c left join ai_execution_task t on t.id=c.execution_id where c.id=?",id);
        if(!rows.isEmpty())projectCallSnapshot(rows.get(0));
    }
    void projectCallSnapshot(Map<String,Object> row) {
        database.transactions().executeWithoutResult(status -> {
            lockAiProjection();
            Instant at=sourceInstant(row.get("created_at"));
            String errorCode=classifyError(row);
            String transport=LOCAL_REJECTIONS.contains(String.valueOf(errorCode))?"LOCAL_REJECTION":"AI_RATE_LIMIT".equals(errorCode)?"RATE_LIMITED":"AI_PROVIDER_TIMEOUT".equals(errorCode)?"TIMED_OUT":"AI_RESPONSE_INVALID".equals(errorCode)?"SUCCEEDED":clean(row.get("transport_outcome"),32);
            String business=LOCAL_REJECTIONS.contains(String.valueOf(errorCode))?"REJECTED":"AI_RESPONSE_INVALID".equals(errorCode)?"FAILED":clean(row.get("business_outcome"),32);
            var previous=database.jdbc().queryForList("select business_outcome,occurred_at,tenant_id,project_id,provider_id,model_id,scene business_scene from ops_ai_call_projection where call_log_id=? for update",row.get("id"));
            // Business parsing failure is a monotonic source revision; a delayed successful hint cannot undo it.
            if(!previous.isEmpty() && "FAILED".equals(previous.get(0).get("business_outcome")) && !"FAILED".equals(business))return;
            // This global projection has one row per source ID across all instances.
            database.jdbc().update("""
                insert into ops_ai_call_projection (call_log_id,occurred_at,tenant_id,project_id,provider_id,model_id,scene,service_type,
                trace_id,execution_id,attempt_id,transport_outcome,business_outcome,status,error_code,duration_ms,prompt_tokens,completion_tokens,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update
                project_id=values(project_id),transport_outcome=values(transport_outcome),business_outcome=values(business_outcome),
                status=values(status),error_code=values(error_code),duration_ms=values(duration_ms),prompt_tokens=values(prompt_tokens),
                completion_tokens=values(completion_tokens),updated_at=values(updated_at)
                """, row.get("id"),ts(at),row.get("tenant_id"),row.get("project_id"),row.get("provider_id"),row.get("model_id"),
                clean(row.get("business_scene"),64),clean(row.get("service_type"),64),clean(row.get("trace_id"),128),row.get("execution_id"),row.get("attempt_id"),
                transport,business,clean(row.get("status"),32),
                errorCode,row.get("duration_ms"),row.get("prompt_tokens"),row.get("completion_tokens"),ts(Instant.now()));
            // Queue each affected dimension once per batch, even if hundreds of IDs change.
            scheduleAggregate(at.truncatedTo(java.time.temporal.ChronoUnit.MINUTES),aiDimensions(row));
            scheduleAggregate(at.truncatedTo(java.time.temporal.ChronoUnit.MINUTES),Map.of("scope","ALL"));
            if(!previous.isEmpty() && !Objects.equals(previous.get(0).get("project_id"),row.get("project_id"))) {
                var old=previous.get(0);Instant oldAt=com.antshorttv.operations.OperationsTime.utc(old.get("occurred_at"));
                scheduleAggregate(oldAt.truncatedTo(java.time.temporal.ChronoUnit.MINUTES),aiDimensions(old));
            }
        });
    }
    private void lockAiProjection() {
        database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values('telemetry.ai.lock','{}',0,?) on duplicate key update setting_key=values(setting_key)",ts(Instant.now()));
        database.jdbc().queryForObject("select version from ops_runtime_setting where setting_key='telemetry.ai.lock' for update",Long.class);
    }
    private void scheduleAggregate(Instant minute,Map<String,Object> dimensions) {
        String key=minute+":"+telemetry.json(dimensions);
        if(!pendingAggregates.add(key))return;
        if(!telemetry.offer(new Write(){
            public void persist(){
                database.transactions().executeWithoutResult(status->{lockAiProjection();rebuildMinute(minute,dimensions);rebuildHour(minute,dimensions);});
                pendingAggregates.remove(key);telemetry.lastAggregateAt=Instant.now();
            }
            public void discarded(){pendingAggregates.remove(key);}
        }))pendingAggregates.remove(key);
    }
    private String classifyError(Map<String,Object> row) {
        if(row.get("error_code")!=null)return clean(row.get("error_code"),64);
        String transport=String.valueOf(row.get("transport_outcome")),business=String.valueOf(row.get("business_outcome"));
        if(transport.equals("TIMED_OUT")||transport.equals("TIMEOUT"))return "AI_PROVIDER_TIMEOUT";
        if(transport.equals("RATE_LIMITED"))return "AI_RATE_LIMIT";
        if(transport.equals("FAILED"))return "AI_PROVIDER_ERROR";
        return business.equals("FAILED")?"AI_RESPONSE_INVALID":null;
    }
    private void rebuildMinute(Instant at,Map<String,Object> dimensions) {
        StringBuilder sql=new StringBuilder("select count(*) count_value, sum(case when transport_outcome in ('FAILED','TIMED_OUT','TIMEOUT','RATE_LIMITED') then 1 else 0 end) error_count, sum(case when business_outcome in ('FAILED','REJECTED') then 1 else 0 end) business_count, coalesce(sum(duration_ms),0) sum_ms, coalesce(max(duration_ms),0) max_ms, coalesce(sum(case when service_type='TEXT' then prompt_tokens else 0 end),0) prompt_tokens, coalesce(sum(case when service_type='TEXT' then completion_tokens else 0 end),0) completion_tokens, sum(case when prompt_tokens is not null and completion_tokens is not null and service_type='TEXT' then 1 else 0 end) token_samples, sum(case when error_code='AI_PROVIDER_TIMEOUT' then 1 else 0 end) timeouts, sum(case when error_code='AI_RATE_LIMIT' then 1 else 0 end) rate_limited");
        for(int i=0;i<18;i++) {
            String lower=i==0?"duration_ms>=0":"duration_ms>"+LatencyHistogram.BOUNDS[i-1];
            String upper=i==17?"":" and duration_ms<="+LatencyHistogram.BOUNDS[i];
            sql.append(",sum(case when ").append(lower).append(upper).append(" then 1 else 0 end) h").append(i);
        }
        sql.append(" from ops_ai_call_projection where occurred_at>=? and occurred_at<?");
        List<Object> args=new ArrayList<>(List.of(ts(at),ts(at.plusSeconds(60))));
        if(!dimensions.containsKey("scope")) {
            for(var pair:Map.of("tenant_id","tenantId","project_id","projectId","provider_id","providerId","model_id","modelId","scene","scene").entrySet()) {
                sql.append(" and (").append(pair.getKey()).append("=? or (").append(pair.getKey()).append(" is null and ? is null))");
                args.add(dimensions.get(pair.getValue()));args.add(dimensions.get(pair.getValue()));
            }
        }
        var row=database.jdbc().queryForMap(sql.toString(),args.toArray());
        long[] counts=new long[18];for(int i=0;i<18;i++)counts[i]=number(row,"h"+i);
        var histogram=new LatencyHistogram();histogram.merge(counts);
        saveGlobalAi(at,"MINUTE",telemetry.json(dimensions),number(row,"count_value"),number(row,"error_count"),number(row,"business_count"),number(row,"sum_ms"),number(row,"max_ms"),histogram,
            Map.of("promptTokens",number(row,"prompt_tokens"),"completionTokens",number(row,"completion_tokens"),"tokenSamples",number(row,"token_samples"),"timeouts",number(row,"timeouts"),"rateLimited",number(row,"rate_limited")));
    }
    private void rebuildHour(Instant minute,Map<String,Object> dimensions) {
        Instant hour=minute.truncatedTo(java.time.temporal.ChronoUnit.HOURS);String json=telemetry.json(dimensions);
        var rows=database.jdbc().queryForList("select count_value,error_count,business_count,sum_ms,max_ms,histogram_json,values_json from ops_metric_bucket where bucket_at>=? and bucket_at<? and granularity='MINUTE' and instance_id='GLOBAL' and family='AI' and dimensions_key=? order by bucket_at limit 60",ts(hour),ts(hour.plusSeconds(3600)),hash(json));
        long count=0,errors=0,business=0,sum=0,max=0;var histogram=new LatencyHistogram();Map<String,Long> values=new HashMap<>();
        for(var row:rows) {
            count+=number(row,"count_value");errors+=number(row,"error_count");business+=number(row,"business_count");sum+=number(row,"sum_ms");max=Math.max(max,number(row,"max_ms"));
            try {histogram.merge(mapper.readValue(row.get("histogram_json").toString(),long[].class));var source=mapper.readValue(row.get("values_json").toString(),Map.class);for(String key:List.of("promptTokens","completionTokens","tokenSamples","timeouts","rateLimited"))values.merge(key,number(source,key),Long::sum);}
            catch(Exception corrupt){throw new IllegalStateException("Invalid persisted AI bucket",corrupt);}
        }
        saveGlobalAi(hour,"HOUR",json,count,errors,business,sum,max,histogram,values);
    }
    private void saveGlobalAi(Instant at,String granularity,String dimensions,long count,long errors,long business,long sum,long max,LatencyHistogram histogram,Map<String,?> values) {
        Map<?,?> typed;try{typed=mapper.readValue(dimensions,Map.class);}catch(Exception invalid){throw new IllegalArgumentException(invalid);}
        database.jdbc().update("""
            insert into ops_metric_bucket (bucket_at,granularity,instance_id,family,dimensions_key,dimensions_json,count_value,error_count,business_count,sum_ms,max_ms,histogram_json,values_json,updated_at,tenant_id,project_id,provider_id,model_id,scene)
            values (?,?,'GLOBAL','AI',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update count_value=values(count_value),error_count=values(error_count),business_count=values(business_count),sum_ms=values(sum_ms),max_ms=values(max_ms),histogram_json=values(histogram_json),values_json=values(values_json),updated_at=values(updated_at),tenant_id=values(tenant_id),project_id=values(project_id),provider_id=values(provider_id),model_id=values(model_id),scene=values(scene)
            """,ts(at),granularity,hash(dimensions),dimensions,count,errors,business,sum,max,telemetry.json(histogram.counts()),telemetry.json(values),ts(Instant.now()),typed.get("tenantId"),typed.get("projectId"),typed.get("providerId"),typed.get("modelId"),typed.get("scene"));
    }
    private Map<String,Object> aiDimensions(Map<String,Object> row) {
        Map<String,Object> dimensions=new TreeMap<>();
        for(String key:List.of("tenant_id","project_id","provider_id","model_id"))dimensions.put(key.substring(0,key.indexOf('_'))+"Id",row.get(key));
        dimensions.put("scene",clean(row.get("business_scene"),64));return dimensions;
    }
    public void projectExecution(long id) {
        var rows=sourceRows("select "+EXEC_FIELDS+" from ai_execution_task where id=?",id);
        if(!rows.isEmpty())projectExecutionSnapshot(rows.get(0));
    }
    public void projectExecutionSnapshot(Map<String,Object> row) {
        database.transactions().executeWithoutResult(transaction->{
        var existing=database.jdbc().queryForList("select status from ops_execution_observation where execution_id=? and execution_version=? for update",row.get("id"),row.get("execution_version"));
        if(!existing.isEmpty() && Set.of("FAILED","TIMED_OUT","CANCELED","SUCCEEDED").contains(String.valueOf(existing.get(0).get("status"))) && Set.of("PENDING","RUNNING").contains(String.valueOf(row.get("status"))))return;
        database.jdbc().update("""
            insert into ops_execution_observation (execution_id,execution_version,tenant_id,project_id,model_id,scene,task_type,status,phase,
            created_at,started_at,completed_at,observed_at,next_run_at,heartbeat_at,claim_expires_at,point_settlement_status,error_code,error_message,trace_id)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update
            status=values(status),model_id=values(model_id),
            phase=values(phase),started_at=values(started_at),completed_at=coalesce(values(completed_at),completed_at),observed_at=values(observed_at),
            next_run_at=values(next_run_at),heartbeat_at=values(heartbeat_at),claim_expires_at=values(claim_expires_at),
            point_settlement_status=values(point_settlement_status),error_code=coalesce(values(error_code),error_code),error_message=coalesce(values(error_message),error_message)
            """,row.get("id"),row.get("execution_version"),row.get("tenant_id"),row.get("project_id"),row.get("model_id"),clean(row.get("scene"),64),clean(row.get("task_type"),64),clean(row.get("status"),32),clean(row.get("phase"),64),
            sourceTimestamp(row.get("created_at")),sourceTimestamp(row.get("started_at")),sourceTimestamp(row.get("completed_at")),ts(Instant.now()),sourceTimestamp(row.get("next_run_at")),sourceTimestamp(row.get("heartbeat_at")),sourceTimestamp(row.get("claim_expires_at")),
            clean(row.get("point_settlement_status"),32),clean(row.get("error_code"),64),clean(row.get("error_message"),500),clean(row.get("trace_id"),128));
        if(Set.of("FAILED","TIMED_OUT").contains(String.valueOf(row.get("status")))) {
            String code=row.get("error_code")==null?"EXECUTION_"+row.get("status"):clean(row.get("error_code"),64);
            String location="AI_EXECUTION/"+clean(row.get("scene"),64)+"/"+clean(row.get("task_type"),32);
            Instant occurred=sourceInstant(row.get("completed_at"));
            database.jdbc().update("""
                insert into ops_event(event_key,occurred_at,source,code,location,message,stack,fingerprint,tenant_id,project_id,execution_id,trace_id,trusted_context,attributes_json)
                values(?,?,'AI_EXECUTION',?,?,?,'',?,?,?,?,?,true,?) on duplicate key update event_key=values(event_key)
                ""","execution-failure:"+row.get("id")+":"+row.get("execution_version"),ts(occurred==null?Instant.now():occurred),code,location,clean(row.get("error_message"),500),hash("AI_EXECUTION:"+code+":"+location),row.get("tenant_id"),row.get("project_id"),row.get("id"),clean(row.get("trace_id"),128),telemetry.json(Map.of("executionVersion",row.get("execution_version"),"completionTimestampMissing",occurred==null)));
        }
        });
    }

    /** Five bounded keyset scans total at most 50 source rows, including older active obligations. */
    public void reconcile(Instant now) {
        for(boolean recent:List.of(true,false)) {
            scanCalls(now,recent);scanExecutions(now,recent);
        }
        scanActiveExecutions(now);
        telemetry.lastReconcileAt=now;
    }
    private void scanCalls(Instant now,boolean recent) {
        String key="telemetry.calls."+(recent?"recent":"sweep");
        database.transactions().executeWithoutResult(status->{
            var cursor=cursor(key,now,recent);
            var rows=sourceRows("select "+CALL_FIELDS+" from ai_call_log c left join ai_execution_task t on t.id=c.execution_id where c.id>? and c.created_at>=? and c.created_at<=? order by c.id limit "+SOURCE_BATCH_SIZE,cursor.id(),cursor.from(),cursor.to());
            for(var row:rows)projectCallSnapshot(row);
            saveCursor(key,rows.size()==SOURCE_BATCH_SIZE?new ScanCursor(number(rows.get(rows.size()-1),"id"),cursor.at(),cursor.from(),cursor.to()):null);
            if(rows.size()<SOURCE_BATCH_SIZE)org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCommit(){synchronized(coverageCandidates){if(coverageCandidates.size()>=8)coverageCandidates.pollFirst();coverageCandidates.addLast(new CoverageRange(sourceInstant(cursor.from()),sourceInstant(cursor.to())));}}
            });
        });
    }
    private void scanExecutions(Instant now,boolean recent) {
        String key="telemetry.executions."+(recent?"recent":"sweep");
        database.transactions().executeWithoutResult(status->{
            var cursor=cursor(key,now,recent);
            var rows=sourceRows("select "+EXEC_FIELDS+" from ai_execution_task where updated_at>=? and updated_at<=? and (updated_at>? or (updated_at=? and id>?)) order by updated_at,id limit "+SOURCE_BATCH_SIZE,cursor.from(),cursor.to(),cursor.at(),cursor.at(),cursor.id());
            for(var row:rows)projectExecutionSnapshot(row);
            Map<String,Object> last=rows.isEmpty()?Map.of():rows.get(rows.size()-1);
            saveCursor(key,rows.size()==SOURCE_BATCH_SIZE?new ScanCursor(number(last,"id"),sourceDateTime(last.get("updated_at")),cursor.from(),cursor.to()):null);
        });
    }
    private void scanActiveExecutions(Instant now) {
        database.transactions().executeWithoutResult(transaction->{
            String key="telemetry.executions.active";
            database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values(?,'{}',0,?) on duplicate key update setting_key=values(setting_key)",key,ts(now));
            var value=database.jdbc().queryForObject("select value_json from ops_runtime_setting where setting_key=? for update",String.class,key);
            long id,upper;String completed;
            try{var node=mapper.readTree(value);id=node.path("id").asLong();upper=node.path("upperId").asLong();completed=node.path("completedAt").asText("");}
            catch(Exception corrupt){throw new IllegalStateException("Invalid active execution cursor",corrupt);}
            String active="(status in ('PENDING','RUNNING') or (status in ('SUCCEEDED','FAILED','TIMED_OUT','CANCELED') and point_settlement_status='RESERVED'))";
            if(upper==0) {
                var highest=database.jdbc().queryForList("select id from ai_execution_task where "+active+" order by id desc limit 1");
                upper=highest.isEmpty()?0:number(highest.get(0),"id");
            }
            var rows=sourceRows("select "+EXEC_FIELDS+" from ai_execution_task where "+active+" and id>? and id<=? order by id limit "+SOURCE_BATCH_SIZE,id,upper);
            for(var row:rows)projectExecutionSnapshot(row);
            boolean finished=rows.size()<SOURCE_BATCH_SIZE || number(rows.get(rows.size()-1),"id")>=upper;
            if(finished){id=0;upper=0;completed=now.toString();}else id=number(rows.get(rows.size()-1),"id");
            database.jdbc().update("update ops_runtime_setting set value_json=?,version=version+1,updated_at=? where setting_key=?",telemetry.json(Map.of("id",id,"upperId",upper,"completedAt",completed)),ts(now),key);
            if(!completed.isBlank()){Instant completeAt=Instant.parse(completed);org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){telemetry.activeSweepAt=completeAt;}});}
        });
    }
    private ScanCursor cursor(String key,Instant now,boolean recent) {
        database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values(?,'{}',0,?) on duplicate key update setting_key=values(setting_key)",key,ts(now));
        var value=database.jdbc().queryForObject("select value_json from ops_runtime_setting where setting_key=? for update",String.class,key);
        try {
            var node=mapper.readTree(value);
            if(node.has("from"))return new ScanCursor(node.path("id").asLong(),LocalDateTime.parse(node.path("at").asText()),LocalDateTime.parse(node.path("from").asText()),LocalDateTime.parse(node.path("to").asText()));
        }catch(Exception invalid){throw new IllegalStateException("Invalid reconciliation cursor",invalid);}
        LocalDateTime from=sourceLocal(now.minusSeconds(recent?300:86400));
        return new ScanCursor(0,from,from,sourceLocal(now));
    }
    private void saveCursor(String key,ScanCursor cursor) {
        String value=cursor==null?"{}":telemetry.json(Map.of("id",cursor.id(),"at",cursor.at().toString(),"from",cursor.from().toString(),"to",cursor.to().toString()));
        database.jdbc().update("update ops_runtime_setting set value_json=?,version=version+1,updated_at=? where setting_key=?",value,ts(Instant.now()),key);
    }
    record ScanCursor(long id,LocalDateTime at,LocalDateTime from,LocalDateTime to) {}
    record CoverageRange(Instant from,Instant through) {}
    void publishCoverage(com.antshorttv.operations.OperationsCoverageService coverage,Instant now) {
        if(!pendingAggregates.isEmpty())return;
        synchronized(coverageCandidates){while(!coverageCandidates.isEmpty()){var range=coverageCandidates.peekFirst();coverage.recordRange("AI","GLOBAL","GLOBAL",range.from(),range.through(),now);coverageCandidates.pollFirst();}}
    }
    private List<Map<String,Object>> sourceRows(String sql,Object... arguments) {
        // DATETIME is a business wall clock, not a UTC Timestamp from the observer's UTC session.
        return database.jdbc().query(sql,new org.springframework.jdbc.core.ColumnMapRowMapper(){
            @Override protected Object getColumnValue(java.sql.ResultSet rows,int index)throws java.sql.SQLException {
                if(rows.getMetaData().getColumnType(index)==java.sql.Types.TIMESTAMP)return rows.getObject(index,LocalDateTime.class);
                return super.getColumnValue(rows,index);
            }
        },arguments);
    }
    static Instant sourceInstant(Object value) {
        if(value==null)return null;
        if(value instanceof Instant instant)return instant;
        return sourceDateTime(value).atZone(ZoneId.systemDefault()).toInstant();
    }
    static LocalDateTime sourceDateTime(Object value) {return value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;}
    static Timestamp sourceTimestamp(Object value) { return ts(sourceInstant(value)); }
    private static LocalDateTime sourceLocal(Instant value) { return LocalDateTime.ofInstant(value,ZoneId.systemDefault()); }
    private static String clean(Object value,int length) {
        if(value==null)return null;
        return OperationsTelemetry.cleanDimension(String.valueOf(value),length);
    }
}
