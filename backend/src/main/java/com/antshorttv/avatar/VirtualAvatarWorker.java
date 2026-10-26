package com.antshorttv.avatar;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ark.avatar.worker-enabled",havingValue="true",matchIfMissing=true)
public class VirtualAvatarWorker {
    private final VirtualAvatarService tasks;
    private ThreadPoolTaskScheduler scheduler;
    public VirtualAvatarWorker(VirtualAvatarService tasks) {this.tasks=tasks;}
    @PostConstruct void start() {
        // Private scheduler: provider I/O must not block the application's other scheduled jobs.
        scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("virtual-avatar-");
        scheduler.setErrorHandler(e->org.slf4j.LoggerFactory.getLogger(getClass()).warn("Virtual avatar dispatch failed: {}",e.getClass().getSimpleName()));
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::poll,Duration.ofSeconds(15));
    }
    public void poll() {for(long id:tasks.due()) tasks.process(id);}
    @PreDestroy void stop() {if(scheduler!=null) scheduler.shutdown();}
}
