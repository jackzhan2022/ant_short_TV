package com.antshorttv.operations.alerts;

import com.antshorttv.operations.OperationsSettingsService;
import com.antshorttv.operations.telemetry.OperationsJobObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Independent evaluation and delivery threads cannot block the business scheduling pool. */
@Component
public class OperationsAlertScheduler {
    private final OperationsAlertService alerts;
    private final OperationsNotificationWorker notifications;
    private final OperationsSettingsService settings;
    private final ObjectProvider<OperationsJobObserver> jobs;
    private final ScheduledExecutorService executor=Executors.newScheduledThreadPool(2,runnable -> {
        var thread=new Thread(runnable,"operations-alerts");thread.setDaemon(true);return thread;
    });
    public OperationsAlertScheduler(OperationsAlertService alerts,OperationsNotificationWorker notifications,
        OperationsSettingsService settings,ObjectProvider<OperationsJobObserver> jobs) {
        this.alerts=alerts;this.notifications=notifications;this.settings=settings;this.jobs=jobs;
    }
    @PostConstruct void start() {
        executor.scheduleWithFixedDelay(()->run("alert-evaluation",()->alerts.evaluate(Instant.now())),5,30,TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(()->run("notification-delivery",()->notifications.deliver(Instant.now())),6,1,TimeUnit.SECONDS);
    }
    private void run(String job,Runnable action) {
        if(!settings.telemetryEnabled()) return;
        var observer=jobs.getIfAvailable();String code="monitoring."+job;
        if(observer!=null)observer.started(code);
        try { action.run(); if(observer!=null)observer.finished(code,null); }
        catch(RuntimeException failure) { if(observer!=null)observer.finished(code,failure); }
    }
    @PreDestroy void stop() { executor.shutdownNow(); }
}
