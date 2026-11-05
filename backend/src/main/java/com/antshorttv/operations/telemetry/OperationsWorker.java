package com.antshorttv.operations.telemetry;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Neither sampler nor writer is scheduled on the shared business scheduler. */
@Component
public class OperationsWorker {
    private final OperationsTelemetry telemetry;
    private final OperationsProjections projections;
    private final OperationsJobObserver jobs;
    private final DataSource businessSource;
    private final ObjectProvider<MeterRegistry> meters;
    private final ObjectProvider<com.antshorttv.execution.AiExecutionPools> pools;
    private final boolean cacheEnabled;
    private double lastCacheOperations,lastCacheErrors;
    private final ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"operations-sampler"));
    private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor(r->daemon(r,"operations-writer"));
    public OperationsWorker(OperationsTelemetry telemetry,OperationsProjections projections,OperationsJobObserver jobs,
        DataSource businessSource,ObjectProvider<MeterRegistry> meters,ObjectProvider<com.antshorttv.execution.AiExecutionPools> pools,@Value("${query-cache.enabled:false}") boolean cacheEnabled) {
        this.telemetry=telemetry;this.projections=projections;this.jobs=jobs;this.businessSource=businessSource;this.meters=meters;this.pools=pools;this.cacheEnabled=cacheEnabled;
    }
    @PostConstruct void start() {
        sampler.scheduleWithFixedDelay(()->safe("resources",this::sample),1,15,TimeUnit.SECONDS);
        writer.scheduleWithFixedDelay(()->safe("flush",telemetry::flush),1,1,TimeUnit.SECONDS);
        writer.scheduleWithFixedDelay(()->safe("reconciliation",()->projections.reconcile(Instant.now())),5,30,TimeUnit.SECONDS);
        writer.scheduleWithFixedDelay(()->safe("job-observation",jobs::flush),2,15,TimeUnit.SECONDS);
        writer.scheduleWithFixedDelay(()->safe("settings",()->{telemetry.settings.current();telemetry.setEnabled(telemetry.settings.telemetryEnabled());}),1,15,TimeUnit.SECONDS);
    }
    private void safe(String job,Runnable action) {
        if(!telemetry.enabled && !Set.of("settings","job-observation").contains(job))return;
        jobs.started("monitoring."+job);
        try{action.run();jobs.finished("monitoring."+job,null);}catch(RuntimeException failure){telemetry.lastFailure=failure.getClass().getSimpleName();jobs.finished("monitoring."+job,failure);}
    }
    void sample() {
        var memory=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("scope","JVM"); values.put("at",Instant.now().toString());values.put("instanceId",telemetry.settings.instanceId());
        values.put("heapUsedBytes",memory.getUsed());values.put("heapMaxBytes",memory.getMax()<0?null:memory.getMax());
        values.put("threads",ManagementFactory.getThreadMXBean().getThreadCount());
        var os=ManagementFactory.getOperatingSystemMXBean();
        double cpu=os instanceof com.sun.management.OperatingSystemMXBean extended?extended.getProcessCpuLoad():-1;
        values.put("cpuPercent",cpu<0?null:cpu*100);
        values.put("dbActive",null);values.put("dbIdle",null);values.put("dbPending",null);values.put("dbMax",null);
        if(businessSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean()!=null) {
            var pool=hikari.getHikariPoolMXBean();values.put("dbActive",pool.getActiveConnections());values.put("dbIdle",pool.getIdleConnections());
            values.put("dbPending",pool.getThreadsAwaitingConnection());values.put("dbMax",hikari.getMaximumPoolSize());
        }
        // Cache health is inferred from observed operations, never by borrowing a business connection or making a Redis probe.
        values.put("redisStatus",cacheEnabled?"UNKNOWN":"NOT_CONFIGURED");
        MeterRegistry registry=meters.getIfAvailable();
        if(registry!=null) {
            Map<String,Double> cache=new TreeMap<>();double operations=0,errors=0;
            for(var meter:registry.find("antv.query.cache").meters()) {
                double total=0;for(var measure:meter.measure())total+=measure.getValue();cache.put(meter.getId().getTags().toString(),total);
                String outcome=meter.getId().getTag("outcome");if("unavailable".equals(outcome))errors+=total;else if(Set.of("hit","miss").contains(outcome))operations+=total;
            }
            if(cacheEnabled)values.put("redisStatus",errors>lastCacheErrors?"ERROR":operations>lastCacheOperations?"UP":"UNKNOWN");
            lastCacheOperations=operations;lastCacheErrors=errors;
            values.put("queryCache",cache);
        }
        var executionPools=pools.getIfAvailable();values.put("workerSlotsStatus",executionPools==null?"NOT_CONFIGURED":"OBSERVED");
        if(executionPools!=null)values.put("workerSlots",executionPools.snapshot());
        var pipeline=telemetry.pipelineSnapshot();
        values.put("monitoringQueueDepth",pipeline.get("queueDepth"));values.put("monitoringDropped",pipeline.get("dropped"));values.put("monitoringDimensionOverflow",pipeline.get("dimensionOverflow"));
        values.put("monitoringLastFlushAt",pipeline.get("lastFlushAt")==null?null:pipeline.get("lastFlushAt").toString());
        values.put("monitoringLastFailure",pipeline.get("lastFailure"));values.put("monitoringActiveSweepAt",pipeline.get("activeSweepAt")==null?null:pipeline.get("activeSweepAt").toString());
        Instant at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        telemetry.offer(()->telemetry.database.transactions().executeWithoutResult(status->{
            telemetry.saveBucket(at,"MINUTE","RESOURCE","{}",1,0,0,0,0,new LatencyHistogram(),values);
            telemetry.saveBucket(at.truncatedTo(java.time.temporal.ChronoUnit.HOURS),"HOUR","RESOURCE","{}",1,0,0,0,0,new LatencyHistogram(),values);
        }));
        telemetry.offer(()->telemetry.recordResourceMinute(at));
    }
    static Thread daemon(Runnable runnable,String name){Thread thread=new Thread(runnable,name);thread.setDaemon(true);return thread;}
    @PreDestroy void stop(){sampler.shutdownNow();writer.shutdownNow();}
}
