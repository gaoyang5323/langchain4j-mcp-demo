package com.kakuiwong.langchain4jdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置。
 * <p>
 * 配置带有 MdcTaskDecorator 的线程池，确保异步任务自动继承 MDC 上下文（traceId 等）。
 * 所有需要传播链路追踪信息的异步操作都应使用此线程池。
 */
@Configuration
public class AsyncConfig {

    @Bean("mdcTaskExecutor")
    public TaskExecutor mdcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mdc-async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
