package com.antshorttv.downloadcenter;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="download-center.worker-enabled",havingValue="true",matchIfMissing=true)
public class DownloadWorker {
    private final DownloadCenterService service;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService leases=Executors.newScheduledThreadPool(1);
    private final Semaphore slots=new Semaphore(2);
    public DownloadWorker(DownloadCenterService service){this.service=service;}
    @Scheduled(fixedDelay=2000,initialDelay=5000) public void dispatch() {
        for(String id:service.queued(2)) {
            if(!slots.tryAcquire())return;
            try {
                if(!service.claim(id)){slots.release();continue;}
                workers.submit(()->{
                    var lease=leases.scheduleAtFixedRate(()->{
                        try{service.heartbeat(id);}catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Download lease renewal failed for {}",id,ex);}
                    },30,30,TimeUnit.SECONDS);
                    try{service.generate(id);}finally{lease.cancel(false);slots.release();}
                });
            }catch(RuntimeException ex){slots.release();throw ex;}
        }
    }
    @Scheduled(fixedDelay=30000,initialDelay=5000) public void cleanup(){service.cleanup();}
    @PreDestroy public void stop(){workers.shutdownNow();leases.shutdownNow();}
}
