package com.antshorttv.operations.telemetry;

import com.antshorttv.monitoring.MonitoringEvent;
import java.lang.reflect.Method;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import static com.antshorttv.operations.telemetry.OperationsTelemetry.ts;

@Aspect
@Component
public class OperationsJobObserver implements SmartInitializingSingleton {
    private final OperationsTelemetry telemetry;
    private final ApplicationContext context;
    private final Environment environment;
    private final Map<String,Job> jobs=new ConcurrentHashMap<>();
    private final String bootId=UUID.randomUUID().toString();
    private com.antshorttv.monitoring.MonitoringProperties monitoring;
    private final ThreadLocal<JobRun> activeRun=new ThreadLocal<>();
    public OperationsJobObserver(OperationsTelemetry telemetry,ApplicationContext context,Environment environment){this.telemetry=telemetry;this.context=context;this.environment=environment;}
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setMonitoringProperties(com.antshorttv.monitoring.MonitoringProperties monitoring){this.monitoring=monitoring;}
    @Override public void afterSingletonsInstantiated() {
        for(String name:context.getBeanDefinitionNames()) {
            Class<?> type=context.getType(name);if(type==null||!type.getName().startsWith("com.antshorttv."))continue;
            type=AopUtils.getTargetClass(context.getBean(name));
            for(Method method:org.springframework.util.ReflectionUtils.getAllDeclaredMethods(type)) {
                Scheduled schedule=AnnotatedElementUtils.findMergedAnnotation(method,Scheduled.class);
                if(schedule==null)continue;
                String code=type.getSimpleName()+"."+method.getName();
                jobs.put(code,registered(code,schedule));
            }
        }
        for(String code:List.of("resources","flush","reconciliation","job-observation","settings"))registerFixedDelay("monitoring."+code,code.equals("flush")?1000:code.equals("reconciliation")?30000:15000);
        registerFixedDelay("monitoring.alert-evaluation",30000);registerFixedDelay("monitoring.notification-delivery",1000);registerFixedDelay("monitoring.cleanup",3600000);
        registerCron("monitoring.hourly-digest","10 30 * * * *",ZoneId.of("Asia/Shanghai"));
        disabledUnlessPresent("CommercialPaymentLifecycleScheduler.processPayments",60000);
        disabledUnlessPresent("CommercialSubscriptionGrantScheduler.processDueSubscriptions",60000);
    }
    public void registerFixedDelay(String code,long intervalMs) {
        var job=new Job(code);job.type="FIXED_DELAY";job.interval=intervalMs;job.expected=next(job,Instant.now());jobs.putIfAbsent(code,job);
    }
    public void registerCron(String code,String expression,ZoneId zone) {
        var job=new Job(code);job.type="CRON";job.expression=expression;job.zone=zone;job.expected=next(job,Instant.now());jobs.putIfAbsent(code,job);
    }
    private void disabledUnlessPresent(String code,long interval) {
        jobs.computeIfAbsent(code,ignored->{var job=new Job(code);job.type="FIXED_DELAY";job.interval=interval;job.enabled=false;return job;});
    }
    Job registered(String code,Scheduled schedule) {
        Job job=new Job(code);
        if(!schedule.cron().isBlank()) {
            job.type="CRON";job.expression=environment.resolvePlaceholders(schedule.cron());job.enabled=!job.expression.equals("-");
            job.zone=schedule.zone().isBlank()?ZoneId.of("Asia/Shanghai"):ZoneId.of(environment.resolvePlaceholders(schedule.zone()));
        } else if(schedule.fixedDelay()>=0 || !schedule.fixedDelayString().isBlank()) {
            job.type="FIXED_DELAY";job.interval=interval(schedule.fixedDelay(),schedule.fixedDelayString(),schedule);
        } else {job.type="FIXED_RATE";job.interval=interval(schedule.fixedRate(),schedule.fixedRateString(),schedule);}
        job.expected=next(job,Instant.now());return job;
    }
    private long interval(long numeric,String expression,Scheduled schedule) {
        String resolved=environment.resolvePlaceholders(expression);
        if(resolved.isBlank())return schedule.timeUnit().toMillis(numeric);
        try{return schedule.timeUnit().toMillis(Long.parseLong(resolved));}catch(NumberFormatException ignored){return Duration.parse(resolved).toMillis();}
    }
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) || execution(* com.antshorttv.operations.alerts.OperationsPersistentDigest.publish(..))")
    public Object observe(ProceedingJoinPoint point)throws Throwable {
        String code=point.getTarget() instanceof com.antshorttv.operations.alerts.OperationsPersistentDigest?"monitoring.hourly-digest":AopUtils.getTargetClass(point.getTarget()).getSimpleName()+"."+((MethodSignature)point.getSignature()).getMethod().getName();
        JobRun previous=activeRun.get();JobRun run=new JobRun();activeRun.set(run);started(code);
        try{Object result=point.proceed();finished(code,run.failure);return result;}catch(Throwable failure){finished(code,failure);throw failure;}
        finally{if(previous==null)activeRun.remove();else activeRun.set(previous);}
    }
    @AfterThrowing(pointcut="execution(* com.antshorttv.commercial.CommercialSubscriptionGrantService.processDue(..)) || execution(* com.antshorttv.video.AiVideoTaskService.pollDueTasks(..))",throwing="failure")
    public void caughtServiceFailure(Throwable failure){var run=activeRun.get();if(run!=null && run.failure==null)run.failure=new IllegalStateException(OperationsTelemetry.cleanDimension(failure.getClass().getSimpleName()+": "+failure.getMessage(),500));}
    public void started(String code){Job job=jobs.computeIfAbsent(code,Job::new);synchronized(job){job.started=Instant.now();job.running=true;if(job.type.equals("FIXED_DELAY"))job.expected=null;else job.expected=next(job,job.started);}}
    public void finished(String code,Throwable failure){Job job=jobs.computeIfAbsent(code,Job::new);synchronized(job){job.finished=Instant.now();job.running=false;job.duration=job.started==null?0:Math.max(0,Duration.between(job.started,job.finished).toMillis());if(failure==null){job.succeeded=job.finished;job.failures=0;}else{job.error=job.finished;job.failures++;job.message=new MonitoringEvent(Instant.EPOCH,"","","",failure.getClass().getSimpleName()+": "+failure.getMessage(),"",null,null,null,null).sanitized().message();telemetry.captureEvent(new MonitoringEvent(job.finished,"JOB",failure.getClass().getSimpleName(),code,job.message,"",null,null,null,null));}if(job.type.equals("FIXED_DELAY"))job.expected=next(job,job.finished);}}
    private Instant next(Job job,Instant from){if(!job.enabled)return null;if(job.type.equals("CRON")){var next=CronExpression.parse(job.expression).next(from.atZone(job.zone));return next==null?null:next.toInstant();}return from.plusMillis(Math.max(1,job.interval));}
    public void flush(){for(Job current:jobs.values()){
        Job job;
        synchronized(current){
            if(current.code.startsWith("monitoring.") && !Set.of("monitoring.settings","monitoring.job-observation").contains(current.code)){
                boolean enabled=telemetry.enabled && (!current.code.equals("monitoring.hourly-digest") || (telemetry.settings.persistentDigestEnabled() && monitoring!=null && monitoring.enabled() && monitoring.feishuWebhookUrl()!=null && !monitoring.feishuWebhookUrl().isBlank()));
                if(current.enabled!=enabled){current.enabled=enabled;current.expected=enabled?next(current,Instant.now()):null;}
            }
            job=current.copy();
        }
        telemetry.database.jdbc().update("""
        insert into ops_job_heartbeat (instance_id,boot_id,job_code,enabled,schedule_type,interval_ms,schedule_expression,expected_at,started_at,finished_at,succeeded_at,error_at,duration_ms,consecutive_failures,error_message,updated_at)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update enabled=values(enabled),expected_at=values(expected_at),started_at=values(started_at),finished_at=values(finished_at),succeeded_at=values(succeeded_at),error_at=values(error_at),duration_ms=values(duration_ms),consecutive_failures=values(consecutive_failures),error_message=values(error_message),updated_at=values(updated_at)
        """,telemetry.settings.instanceId(),bootId,job.code,job.enabled,job.type,job.interval,job.expression,ts(job.expected),ts(job.started),ts(job.finished),ts(job.succeeded),ts(job.error),job.duration,job.failures,job.message,ts(Instant.now()));}}
    static final class Job {
        final String code;String type="UNKNOWN",expression,message;long interval,duration,failures;boolean enabled=true,running;ZoneId zone=ZoneId.systemDefault();Instant expected,started,finished,succeeded,error;
        Job(String code){this.code=code;}
        Job copy(){var copy=new Job(code);copy.type=type;copy.expression=expression;copy.message=message;copy.interval=interval;copy.duration=duration;copy.failures=failures;copy.enabled=enabled;copy.running=running;copy.zone=zone;copy.expected=expected;copy.started=started;copy.finished=finished;copy.succeeded=succeeded;copy.error=error;return copy;}
    }
    private static final class JobRun {Throwable failure;}
}
