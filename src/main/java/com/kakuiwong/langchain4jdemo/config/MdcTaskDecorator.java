package com.kakuiwong.langchain4jdemo.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 上下文传播装饰器。
 * <p>
 * 实现 Spring TaskDecorator 接口，在任务提交时捕获调用线程的 MDC 上下文，
 * 在任务执行前恢复到工作线程，执行后清理，实现 traceId 等链路信息的自动透传。
 * <p>
 * 使用方式：将此装饰器设置到 ThreadPoolTaskExecutor 上，
 * 所有通过该线程池执行的任务都会自动继承提交线程的 MDC 上下文。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在提交线程捕获 MDC 上下文快照
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                // 在工作线程中恢复 MDC 上下文
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                // 执行完毕清理，避免线程复用时上下文污染
                MDC.clear();
            }
        };
    }
}
