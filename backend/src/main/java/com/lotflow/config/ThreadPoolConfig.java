package com.lotflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/** AI、WebSocket、消息和调度任务的隔离线程池。 */
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        return executor("lotflow-ai-", 4, 16, 200);
    }

    @Bean(name = "messageTaskExecutor")
    public ThreadPoolTaskExecutor messageTaskExecutor() {
        return executor("lotflow-message-", 2, 8, 500);
    }

    @Bean(name = "websocketTaskExecutor")
    public ThreadPoolTaskExecutor websocketTaskExecutor() {
        return executor("lotflow-ws-", 2, 16, 1000);
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("lotflow-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }

    private ThreadPoolTaskExecutor executor(String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        // 队列满时直接拒绝，让过载显式暴露给调用方，不能用无界队列拖垮进程。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
