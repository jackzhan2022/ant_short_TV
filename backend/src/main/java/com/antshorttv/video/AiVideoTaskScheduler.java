package com.antshorttv.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.video.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AiVideoTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiVideoTaskScheduler.class);

    private final AiVideoTaskService aiVideoTaskService;

    public AiVideoTaskScheduler(AiVideoTaskService aiVideoTaskService) {
        this.aiVideoTaskService = aiVideoTaskService;
    }

    @Scheduled(fixedDelayString = "${ai.video.poll-fixed-delay-ms:15000}")
    public void pollDueTasks() {
        try {
            aiVideoTaskService.pollDueTasks();
        } catch (Exception exception) {
            LOGGER.warn("AI video due task polling failed.", exception);
        }
    }
}
