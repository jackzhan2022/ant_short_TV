package com.antshorttv.commercial;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "commercial.subscription.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CommercialSubscriptionGrantScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommercialSubscriptionGrantScheduler.class);
    private final CommercialSubscriptionGrantService service;

    public CommercialSubscriptionGrantScheduler(CommercialSubscriptionGrantService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${commercial.subscription.scheduler.fixed-delay-ms:60000}")
    public void processDueSubscriptions() {
        try {
            service.processDue(LocalDateTime.now());
        } catch (RuntimeException exception) {
            LOGGER.error("Commercial subscription scheduler failed", exception);
        }
    }
}
