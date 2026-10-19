package com.antshorttv.execution;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiExecutionExecutorConfiguration {
    @Bean("aiExecutionTaskExecutor")
    ThreadPoolTaskExecutor aiExecutionTaskExecutor(
        @Value("${ai.execution.worker-threads:4}") int workerThreads,
        @Value("${ai.execution.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerThreads);
        executor.setMaxPoolSize(workerThreads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-execution-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("aiImagePublicationExecutor")
    ThreadPoolTaskExecutor aiImagePublicationExecutor(
        @Value("${ai.execution.image-publication.worker-threads:2}") int workerThreads,
        @Value("${ai.execution.image-publication.queue-capacity:32}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerThreads);
        executor.setMaxPoolSize(workerThreads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-image-publication-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
