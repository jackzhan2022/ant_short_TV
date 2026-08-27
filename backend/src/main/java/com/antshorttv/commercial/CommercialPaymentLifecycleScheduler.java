package com.antshorttv.commercial;

import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "commercial.wechat.enabled", havingValue = "true")
public class CommercialPaymentLifecycleScheduler {
    private final CommercialPaymentLifecycleService service;
    public CommercialPaymentLifecycleScheduler(CommercialPaymentLifecycleService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${commercial.payment.scheduler.fixed-delay-ms:60000}")
    public void processPayments() {
        service.closeExpired(LocalDateTime.now());
        service.retryPendingEntitlements();
    }
}
