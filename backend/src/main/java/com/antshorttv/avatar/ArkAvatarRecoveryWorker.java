package com.antshorttv.avatar;

import com.antshorttv.video.SeedanceAvatarRecoveryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ark.avatar.worker-enabled",havingValue="true",matchIfMissing=true)
public class ArkAvatarRecoveryWorker {
    private final SeedanceAvatarRecoveryService recovery;
    private final ArkAssetPool assets;
    private ThreadPoolTaskScheduler scheduler;
    private Instant nextInventory=Instant.EPOCH;
    public ArkAvatarRecoveryWorker(SeedanceAvatarRecoveryService recovery,ArkAssetPool assets) {this.recovery=recovery;this.assets=assets;}
    @PostConstruct void start() {
        scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("avatar-recovery-");
        scheduler.setErrorHandler(e -> org.slf4j.LoggerFactory.getLogger(getClass()).warn("Avatar recovery dispatch failed: {}",e.getClass().getSimpleName()));
        scheduler.initialize();scheduler.scheduleWithFixedDelay(this::poll,Duration.ofSeconds(15));
    }
    public void poll() {
        if(assets.configured() && !Instant.now().isBefore(nextInventory)) {
            try {assets.maintain();nextInventory=Instant.now().plusSeconds(300);}
            catch(Exception e) {nextInventory=Instant.now().plusSeconds(30);org.slf4j.LoggerFactory.getLogger(getClass()).warn("Ark asset inventory deferred: {}",e.getClass().getSimpleName());}
        }
        recovery.pollDue();
    }
    @PreDestroy void stop() {if(scheduler!=null)scheduler.shutdown();}
}
