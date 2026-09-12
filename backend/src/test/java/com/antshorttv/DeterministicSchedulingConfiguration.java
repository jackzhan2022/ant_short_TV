package com.antshorttv;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

/** Tests invoke scheduled jobs explicitly; application scheduling stays unconditional. */
@Configuration(proxyBeanMethods = false)
public class DeterministicSchedulingConfiguration {
    @Bean
    TaskScheduler taskScheduler() {
        return Mockito.mock(TaskScheduler.class);
    }
}
