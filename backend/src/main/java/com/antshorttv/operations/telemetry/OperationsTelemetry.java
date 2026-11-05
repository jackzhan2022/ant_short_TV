package com.antshorttv.operations.telemetry;

import com.antshorttv.monitoring.MonitoringEvent;
import com.antshorttv.operations.OperationsDatabase;
import com.antshorttv.operations.OperationsSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Capture methods do no IO and never wait for queue capacity or database connections. */
@Component
public class OperationsTelemetry {
    public record HttpObservation(Instant at, String route, String method, String outcome,
        long durationMs, String requestId, long sqlCount, long sqlMs) {}
    interface Write { void persist(); default void discarded() {} }
    record QueuedWrite(Instant queuedAt, Write write) {}
    final OperationsDatabase database;
    final OperationsSettingsService settings;
    final ObjectMapper mapper;
    final ArrayBlockingQueue<QueuedWrite> queue;
    final AtomicLong dropped = new AtomicLong(), dimensionOverflow = new AtomicLong();
    final ReentrantLock metricsLock = new ReentrantLock();
    final LinkedHashMap<String, HttpBucket> metrics = new LinkedHashMap<>();
    final Map<Instant,Integer> dimensionCounts = new HashMap<>();
    final Map<String,String> httpDimensions=new HashMap<>();
    private Instant dimensionsMinute;
    volatile boolean enabled = true;
    volatile Instant lastFlushAt, lastAggregateAt, lastReconcileAt, activeSweepAt;
    volatile String lastFailure;
    private Instant retryAt = Instant.EPOCH;
    private int failures;
    private volatile OperationsProjections projections;
    private final String bootId=UUID.randomUUID().toString();
    private com.antshorttv.operations.OperationsCoverageService coverage;
    private Instant coverageFrom=Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(60),httpSeededThrough=coverageFrom,resourceCoverageFrom=coverageFrom,resourceThrough=coverageFrom,lastCoverageFlush;
    private final NavigableSet<Instant> resourceMinutes=new TreeSet<>();
    private long coverageDropped,coverageOverflow;
    private final AtomicLong enabledChanges=new AtomicLong();
    private long coverageEnabledChanges;
    private boolean coverageInvalidationPending;
    private boolean coverageRegistered;

    @Autowired public OperationsTelemetry(OperationsDatabase database, OperationsSettingsService settings, ObjectMapper mapper) {
        this(database, settings, mapper, 5000);
    }
    OperationsTelemetry(OperationsDatabase database, OperationsSettingsService settings, ObjectMapper mapper, int capacity) {
        this.database=database; this.settings=settings; this.mapper=mapper; queue=new ArrayBlockingQueue<>(capacity);
    }
    @Autowired void projections(@org.springframework.context.annotation.Lazy OperationsProjections projections) { this.projections=projections; }
    @Autowired(required=false) void coverage(com.antshorttv.operations.OperationsCoverageService coverage){this.coverage=coverage;}
    void setEnabled(boolean value){if(enabled!=value){enabled=value;enabledChanges.incrementAndGet();}}

    public void captureEvent(MonitoringEvent original) {
        captureEvent(original,null);
    }
    private void captureEvent(MonitoringEvent original,HttpObservation observation) {
        if (!enabled || original == null) return;
        try {
            MonitoringEvent event=original.sanitized();
            String key=UUID.randomUUID().toString();
            boolean trusted=event.source()!=null && !event.source().startsWith("FRONTEND_") && !event.source().equals("API");
            String fingerprint=hash(event.source()+":"+event.code()+":"+event.location());
            var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            var request=attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet?servlet.getRequest():null;
            String requestId=observation!=null?cleanDimension(observation.requestId(),128):request==null?null:cleanDimension((String)request.getAttribute("operations.requestId"),128);
            Long duration=observation!=null?Long.valueOf(observation.durationMs()):request!=null && request.getAttribute("operations.startedNanos") instanceof Long started?Math.max(0,(System.nanoTime()-started)/1_000_000):null;
            long[] sql=com.antshorttv.observability.DatabaseQueryObservation.snapshot();
            long sqlCount=observation==null?sql[0]:observation.sqlCount(),sqlMs=observation==null?sql[1]/1_000_000:observation.sqlMs();
            offer(() -> {
                try {
                    database.jdbc().update("""
                        insert into ops_event (event_key,occurred_at,source,code,location,message,stack,fingerprint,
                        user_id,tenant_id,project_id,execution_id,trusted_context,attributes_json,request_id,duration_ms,sql_count,sql_ms)
                        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, key,ts(event.occurredAt()),event.source(),event.code(),event.location(),event.message(),event.stack(),fingerprint,
                        event.userId(),trusted?event.tenantId():null,trusted?event.projectId():null,event.taskId(),trusted,
                        json(trusted?Map.of():Map.of("clientTenantHint",String.valueOf(event.tenantId()),"clientProjectHint",String.valueOf(event.projectId()))),requestId,duration,sqlCount,sqlMs);
                } catch (org.springframework.dao.DuplicateKeyException duplicate) { /* committed retry */ }
            });
        } catch (RuntimeException invalid) { dropped.incrementAndGet(); }
    }

    public void recordHttp(HttpObservation observation) {
        if (!enabled || observation == null) return;
        if (!metricsLock.tryLock()) { dropped.incrementAndGet(); return; }
        try {
            Instant minute=observation.at().truncatedTo(ChronoUnit.MINUTES);
            // A fixed three-minute memory horizon makes database outages bounded.
            var iterator=metrics.entrySet().iterator();
            while(iterator.hasNext()) {
                var bucket=iterator.next().getValue();
                if (bucket.at.isBefore(minute.minusSeconds(120))) { if(bucket.dirty) dropped.addAndGet(bucket.count); iterator.remove(); dimensionCounts.remove(bucket.at); }
            }
            String outcome=Set.of("SUCCESS","TECHNICAL_ERROR","BUSINESS_REJECTION","CANCELED").contains(observation.outcome())?observation.outcome():"TECHNICAL_ERROR";
            if(!minute.equals(dimensionsMinute)){httpDimensions.clear();dimensionsMinute=minute;}
            String route=observation.route()==null?"UNMATCHED":observation.route().substring(0,Math.min(300,observation.route().length()));
            String method=observation.method()==null?"UNKNOWN":observation.method().substring(0,Math.min(12,observation.method().length()));
            String dimensionIdentity=route+":"+method+":"+outcome;
            String dimensions=httpDimensions.get(dimensionIdentity);
            if(dimensions==null) {
                dimensions=json(new TreeMap<>(Map.of("route",cleanDimension(route,300),"method",cleanDimension(method,12),"outcome",outcome)));
                if(httpDimensions.size()<2000)httpDimensions.put(dimensionIdentity,dimensions);
            }
            String key=minute+":"+dimensions;
            if (!metrics.containsKey(key) && dimensionCounts.getOrDefault(minute,0)>=2000) {
                dimensionOverflow.incrementAndGet(); dimensions="{\"method\":\"OTHER\",\"outcome\":\"OTHER\",\"route\":\"OTHER\"}"; key=minute+":"+dimensions;
            }
            HttpBucket bucket=metrics.get(key);
            if(bucket==null) { bucket=new HttpBucket(minute,dimensions); metrics.put(key,bucket); dimensionCounts.merge(minute,1,Integer::sum); }
            addHttp(bucket,observation,outcome);
            String all="{\"scope\":\"ALL\"}";
            addHttp(metrics.computeIfAbsent(minute+":"+all,ignored->new HttpBucket(minute,all)),observation,outcome);
        } catch(RuntimeException invalid) { dropped.incrementAndGet(); }
        finally { metricsLock.unlock(); }
        if(observation.durationMs()>=3000)captureEvent(new MonitoringEvent(observation.at(),"HTTP","SLOW_REQUEST",observation.method()+" "+observation.route(),"Interactive request exceeded 3000ms","",null,null,null,null),observation);
    }

    private void addHttp(HttpBucket bucket,HttpObservation observation,String outcome) {
        bucket.count++;bucket.errors+=outcome.equals("TECHNICAL_ERROR")?1:0;bucket.business+=outcome.equals("BUSINESS_REJECTION")?1:0;
        long duration=Math.max(0,observation.durationMs());bucket.sum+=duration;bucket.max=Math.max(bucket.max,duration);
        bucket.histogram.record(duration);bucket.sqlCount+=Math.max(0,observation.sqlCount());bucket.sqlMs+=Math.max(0,observation.sqlMs());bucket.dirty=true;
    }

    public void captureAiCall(long id) { afterCommit(() -> offer(() -> projections.projectCall(id))); }
    public void captureExecution(long id) { afterCommit(() -> offer(() -> projections.projectExecution(id))); }
    public void captureExecutionSnapshot(Map<String,Object> snapshot) {
        if (!enabled) return;
        Map<String,Object> copy=Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        afterCommit(() -> offer(() -> projections.projectExecutionSnapshot(copy)));
    }
    public void afterCommit(Runnable capture) {
        if (!enabled) return;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { safely(capture); }
                });
            } else safely(capture);
        } catch (RuntimeException ignored) { dropped.incrementAndGet(); }
    }
    boolean offer(Write write) {
        if(!enabled)return false;
        if(queue.offer(new QueuedWrite(Instant.now(),write)))return true;
        dropped.incrementAndGet();return false;
    }
    private void safely(Runnable action) { try { action.run(); } catch(RuntimeException ignored) { dropped.incrementAndGet(); } }

    /** Called by the independent writer once per second; failed head remains for bounded retry. */
    public void flush() { flush(Instant.now()); }
    synchronized void flush(Instant now) {
        while(queue.peek()!=null && queue.peek().queuedAt().isBefore(now.minusSeconds(60))) { queue.poll().write().discarded();dropped.incrementAndGet(); }
        prepareCoverage(now);
        if(now.isBefore(retryAt)) return;
        try {
            int budget=100;
            while(budget > 0) { QueuedWrite head=queue.peek(); if(head==null)break; head.write().persist(); queue.poll();budget--; }
            List<HttpBucket> pending=new ArrayList<>();
            metricsLock.lock();
            try { for(var bucket:metrics.values()) if(bucket.dirty && pending.size()<budget) pending.add(bucket.copy()); }
            finally { metricsLock.unlock(); }
            for(var bucket:pending) {
                database.transactions().executeWithoutResult(status -> {
                    saveHttp(bucket);
                    recomputeHour(bucket);
                });
                metricsLock.lock();
                try { var current=metrics.get(bucket.at+":"+bucket.dimensions); if(current!=null && current.count==bucket.count)current.dirty=false; }
                finally { metricsLock.unlock(); }
            }
            if(!pending.isEmpty())lastAggregateAt=now;
            lastFlushAt=now; failures=0; lastFailure=null;
            publishCoverage(now);
        } catch(RuntimeException failure) {
            failures=Math.min(6,failures+1); retryAt=now.plusSeconds(Math.min(30,1L<<failures));
            lastFailure=failure.getClass().getSimpleName();
        }
    }
    private void prepareCoverage(Instant now) {
        if(coverage==null)return;
        if(coverageEnabledChanges!=enabledChanges.get() || coverageDropped!=dropped.get() || coverageOverflow!=dimensionOverflow.get() || (lastCoverageFlush!=null && lastCoverageFlush.plusSeconds(60).isBefore(now))) {
            coverageFrom=now.truncatedTo(ChronoUnit.MINUTES).plusSeconds(60);httpSeededThrough=coverageFrom;resourceCoverageFrom=coverageFrom;resourceThrough=coverageFrom;resourceMinutes.clear();coverageInvalidationPending=true;
            coverageDropped=dropped.get();coverageOverflow=dimensionOverflow.get();
            coverageEnabledChanges=enabledChanges.get();
        }
        Instant through=now.minusSeconds(10).truncatedTo(ChronoUnit.MINUTES);
        metricsLock.lock();
        try {
            var iterator=metrics.entrySet().iterator();while(iterator.hasNext()){var bucket=iterator.next().getValue();if(bucket.at.isBefore(now.truncatedTo(ChronoUnit.MINUTES).minusSeconds(120))){if(bucket.dirty)dropped.addAndGet(bucket.count);iterator.remove();dimensionCounts.remove(bucket.at);}}
            if(httpSeededThrough.isBefore(through.minusSeconds(180)))httpSeededThrough=through.minusSeconds(180);
            while(httpSeededThrough.isBefore(through)) {
                Instant minute=httpSeededThrough;String all="{\"scope\":\"ALL\"}";
                if(!metrics.containsKey(minute+":"+all)){var bucket=new HttpBucket(minute,all);bucket.dirty=true;metrics.put(minute+":"+all,bucket);}
                httpSeededThrough=httpSeededThrough.plusSeconds(60);
            }
        }finally{metricsLock.unlock();}
    }
    void recordResourceMinute(Instant minute){resourceMinutes.add(minute);while(resourceMinutes.size()>4)resourceMinutes.pollFirst();}
    private void publishCoverage(Instant now) {
        if(coverage==null)return;
        if(coverageDropped!=dropped.get() || coverageOverflow!=dimensionOverflow.get())prepareCoverage(now);
        String instance=settings.instanceId();
        if(coverageInvalidationPending){for(String family:List.of("HTTP","RESOURCE"))coverage.invalidate(family,instance,bootId,now);coverage.invalidate("AI","GLOBAL","GLOBAL",now);coverageInvalidationPending=false;}
        if(!coverageRegistered){
            for(String family:List.of("HTTP","RESOURCE"))coverage.recordRange(family,instance,bootId,coverageFrom,coverageFrom,now);
            coverageRegistered=true;
        }
        Instant through=now.minusSeconds(10).truncatedTo(ChronoUnit.MINUTES);boolean dirty;
        metricsLock.lock();try{dirty=metrics.values().stream().anyMatch(bucket->bucket.dirty && bucket.at.isBefore(through));}finally{metricsLock.unlock();}
        if(queue.isEmpty() && !dirty && coverageFrom.isBefore(through))coverage.recordRange("HTTP",instance,bootId,coverageFrom,through,now);
        resourceMinutes.headSet(resourceThrough,false).clear();
        if(!resourceMinutes.isEmpty() && resourceMinutes.first().isAfter(resourceThrough)) {
            resourceCoverageFrom=now.truncatedTo(ChronoUnit.MINUTES).plusSeconds(60);resourceThrough=resourceCoverageFrom;resourceMinutes.clear();coverage.invalidate("RESOURCE",instance,bootId,now);
        }
        while(resourceMinutes.contains(resourceThrough) && !resourceThrough.plusSeconds(60).isAfter(through))resourceThrough=resourceThrough.plusSeconds(60);
        if(resourceCoverageFrom.isBefore(resourceThrough))coverage.recordRange("RESOURCE",instance,bootId,resourceCoverageFrom,resourceThrough,now);
        if(queue.isEmpty() && projections!=null)projections.publishCoverage(coverage,now);
        lastCoverageFlush=now;
    }

    private void saveHttp(HttpBucket bucket) {
        var rows=database.jdbc().queryForList("select count_value,error_count,business_count,sum_ms,max_ms,histogram_json,values_json from ops_metric_bucket where bucket_at=? and granularity='MINUTE' and instance_id=? and family='HTTP' and dimensions_key=? for update",ts(bucket.at),settings.instanceId(),hash(bucket.dimensions));
        Map<String,Object> old=rows.isEmpty()?Map.of():rows.get(0);Map<?,?> values=Map.of();long[] oldHistogram=new long[18],previousLocal=new long[18];
        try {
            if(!old.isEmpty()){oldHistogram=mapper.readValue(old.get("histogram_json").toString(),long[].class);values=mapper.readValue(old.get("values_json").toString(),Map.class);}
            if(bootId.equals(values.get("bootId"))) previousLocal=mapper.convertValue(values.get("localHistogram"),long[].class);
        }catch(Exception corrupt){throw new IllegalStateException("Invalid persisted HTTP bucket",corrupt);}
        boolean own=bootId.equals(values.get("bootId"));Map<?,?> prior=own?values:Map.of();
        long[] total=bucket.histogram.counts();for(int i=0;i<18;i++)total[i]+=oldHistogram[i]-previousLocal[i];
        var histogram=new LatencyHistogram();histogram.merge(total);
        var updated=new LinkedHashMap<String,Object>();updated.put("bootId",bootId);updated.put("localHistogram",bucket.histogram.counts());
        updated.put("localCount",bucket.count);updated.put("localErrors",bucket.errors);updated.put("localBusiness",bucket.business);updated.put("localSum",bucket.sum);
        updated.put("localSqlCount",bucket.sqlCount);updated.put("localSqlMs",bucket.sqlMs);
        updated.put("sqlCount",number(values,"sqlCount")+bucket.sqlCount-number(prior,"localSqlCount"));updated.put("sqlMs",number(values,"sqlMs")+bucket.sqlMs-number(prior,"localSqlMs"));
        saveBucket(bucket.at,"MINUTE","HTTP",bucket.dimensions,number(old,"count_value")+bucket.count-number(prior,"localCount"),
            number(old,"error_count")+bucket.errors-number(prior,"localErrors"),number(old,"business_count")+bucket.business-number(prior,"localBusiness"),
            number(old,"sum_ms")+bucket.sum-number(prior,"localSum"),Math.max(number(old,"max_ms"),bucket.max),histogram,updated);
    }

    void saveBucket(Instant at,String granularity,String family,String dimensions,long count,long errors,long business,
        long sum,long max,LatencyHistogram histogram,Map<String,?> values) {
        Map<?,?> typed=Map.of();
        if(family.equals("HTTP"))try{typed=mapper.readValue(dimensions,Map.class);}catch(Exception invalid){throw new IllegalArgumentException(invalid);}
        database.jdbc().update("""
            insert into ops_metric_bucket (bucket_at,granularity,instance_id,family,dimensions_key,dimensions_json,
            count_value,error_count,business_count,sum_ms,max_ms,histogram_json,values_json,updated_at,route,method)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update
            count_value=values(count_value),error_count=values(error_count),business_count=values(business_count),
            sum_ms=values(sum_ms),max_ms=values(max_ms),histogram_json=values(histogram_json),values_json=values(values_json),updated_at=values(updated_at),route=values(route),method=values(method)
            """,ts(at),granularity,settings.instanceId(),family,hash(dimensions),dimensions,count,errors,business,sum,max,json(histogram.counts()),json(values),ts(Instant.now()),typed.get("route"),typed.get("method"));
    }
    private void recomputeHour(HttpBucket bucket) {
        Instant hour=bucket.at.truncatedTo(ChronoUnit.HOURS);
        var rows=database.jdbc().queryForList("select count_value,error_count,business_count,sum_ms,max_ms,histogram_json,values_json from ops_metric_bucket where bucket_at>=? and bucket_at<? and granularity='MINUTE' and instance_id=? and family='HTTP' and dimensions_key=?",
            ts(hour),ts(hour.plusSeconds(3600)),settings.instanceId(),hash(bucket.dimensions));
        long count=0,errors=0,business=0,sum=0,max=0,sqlCount=0,sqlMs=0; var histogram=new LatencyHistogram();
        for(var row:rows) {
            count+=number(row,"count_value");errors+=number(row,"error_count");business+=number(row,"business_count");sum+=number(row,"sum_ms");max=Math.max(max,number(row,"max_ms"));
            try { histogram.merge(mapper.readValue(String.valueOf(row.get("histogram_json")),long[].class));
                var values=mapper.readValue(String.valueOf(row.get("values_json")),Map.class); sqlCount+=number(values,"sqlCount");sqlMs+=number(values,"sqlMs");
            } catch(Exception invalid) { throw new IllegalStateException("Invalid persisted histogram",invalid); }
        }
        saveBucket(hour,"HOUR","HTTP",bucket.dimensions,count,errors,business,sum,max,histogram,Map.of("sqlCount",sqlCount,"sqlMs",sqlMs));
    }
    public Map<String,Object> pipelineSnapshot() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("queueDepth",queue.size()); result.put("dropped",dropped.get()); result.put("dimensionOverflow",dimensionOverflow.get());
        result.put("lastFlushAt",lastFlushAt); result.put("lastAggregateAt",lastAggregateAt);result.put("lastReconcileAt",lastReconcileAt);
        result.put("activeSweepAt",activeSweepAt);
        result.put("lastFailure",lastFailure);result.put("telemetryEnabled",enabled);
        result.put("projectionCoverage","PARTIAL");result.put("reconciliationHorizonHours",24);
        result.put("coverageReason","Asynchronous snapshots can be lost; current source rows cannot reconstruct missed execution versions.");
        return result;
    }
    static Timestamp ts(Instant at) { return at==null?null:Timestamp.from(at); }
    static long number(Map<?,?> row,String key) { Object value=row.get(key);return value instanceof Number n?n.longValue():0; }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    String json(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception failure){throw new IllegalArgumentException(failure);} }
    static String cleanDimension(String value,int length) {
        String clean=new MonitoringEvent(Instant.EPOCH,"","","",value,"",null,null,null,null).sanitized().message();
        return clean.substring(0,Math.min(length,clean.length()));
    }
    static final class HttpBucket {
        final Instant at; final String dimensions; final LatencyHistogram histogram=new LatencyHistogram();
        long count,errors,business,sum,max,sqlCount,sqlMs; boolean dirty;
        HttpBucket(Instant at,String dimensions){this.at=at;this.dimensions=dimensions;}
        HttpBucket copy(){var b=new HttpBucket(at,dimensions);b.count=count;b.errors=errors;b.business=business;b.sum=sum;b.max=max;b.sqlCount=sqlCount;b.sqlMs=sqlMs;b.histogram.merge(histogram);return b;}
    }
}
