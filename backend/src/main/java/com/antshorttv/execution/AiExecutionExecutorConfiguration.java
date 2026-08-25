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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
