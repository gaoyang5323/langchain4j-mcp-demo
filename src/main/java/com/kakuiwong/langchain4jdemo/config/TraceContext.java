package com.kakuiwong.langchain4jdemo.config;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文工具。
 * <p>
 * 基于 SLF4J MDC 实现日志中 traceId 的输出。
 * traceId 通过方法参数在各层之间显式传递，不依赖 MDC 跨线程传播。
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SESSION_ID_KEY = "sessionId";

    private TraceContext() {
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void setSessionId(String sessionId) {
        MDC.put(SESSION_ID_KEY, sessionId);
    }

    public static void clear() {
        MDC.clear();
    }
}
