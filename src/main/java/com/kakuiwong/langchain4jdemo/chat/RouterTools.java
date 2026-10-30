package com.kakuiwong.langchain4jdemo.chat;

import com.kakuiwong.langchain4jdemo.config.TraceContext;
import dev.langchain4j.agent.tool.Tool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 路由工具：供 RouterAssistant 调用，将请求转发到对应的子服务。
 * <p>
 * traceId 传播说明：
 * RouterChatService 在调用 AiServices 前已通过 TraceContext.setTraceId() 设置 MDC。
 * LangChain4j 内部线程池会丢失 MDC，因此 RouterTools 在 executeWithTracing 中
 * 再次从当前线程 MDC 读取（如果 LangChain4j 碰巧在同一线程调用），
 * 或者从 mdcTaskExecutor 传播的异步线程中读取。
 * <p>
 * 最终兜底方案：通过构造函数将 traceId 直接传入 RouterTools 实例字段。
 * 每次请求创建新的 RouterTools 实例不现实（AiServices 构建时已绑定），
 * 所以使用 volatile 字段在每次请求时更新。
 */
public class RouterTools {

    private static final Logger log = LoggerFactory.getLogger(RouterTools.class);

    private final WeatherChatService weatherChatService;
    private final FoodChatService foodChatService;
    private final long timeoutSeconds;
    private final MeterRegistry meterRegistry;
    private final Executor mdcTaskExecutor;

    /**
     * 当前请求的 traceId，由 RouterChatService 在每次请求时设置。
     * 使用 volatile 保证对 LangChain4j 内部线程的可见性。
     */
    private volatile String currentTraceId;

    public RouterTools(WeatherChatService weatherChatService,
                       FoodChatService foodChatService,
                       long timeoutSeconds,
                       MeterRegistry meterRegistry,
                       Executor mdcTaskExecutor) {
        this.weatherChatService = weatherChatService;
        this.foodChatService = foodChatService;
        this.timeoutSeconds = timeoutSeconds;
        this.meterRegistry = meterRegistry;
        this.mdcTaskExecutor = mdcTaskExecutor;
    }

    /**
     * 设置当前请求的 traceId，由 RouterChatService 在调用 AiServices 前调用。
     */
    public void setCurrentTraceId(String traceId) {
        this.currentTraceId = traceId;
    }

    @Tool("查询天气相关信息。当用户询问天气、气温、降雨、风力、空气质量等气象问题时使用此工具。" +
            "参数为用户问题中包含的城市或地区名称。")
    public String weatherQuery(String question) {
        return executeWithTracing("weatherQuery", question,
                () -> weatherChatService.chat(question));
    }

    @Tool("查询美食相关信息。当用户询问食物、菜谱、烹饪、饮食、特产、餐厅等美食相关问题时使用此工具。" +
            "参数为用户问题中包含的城市或地区名称。")
    public String foodQuery(String question) {
        return executeWithTracing("foodQuery", question,
                () -> foodChatService.chat(question));
    }

    private String executeWithTracing(String toolName, String question, ToolTask task) {
        // 从 volatile 字段获取 traceId（由 RouterChatService 在请求开始时设置）
        String traceId = currentTraceId;
        if (traceId != null) {
            TraceContext.setTraceId(traceId);
        }

        long start = System.nanoTime();

        log.info("[RouterTools] {} 开始执行, question={}, traceId={}",
                toolName, truncate(question, 80), traceId);

        String status = "success";
        try {
            String result = CompletableFuture.supplyAsync(() -> {
                try {
                    return task.execute();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, mdcTaskExecutor).get(timeoutSeconds, TimeUnit.SECONDS);

            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[RouterTools] {} 执行成功, 耗时={}ms, traceId={}",
                    toolName, costMs, traceId);
            return result;

        } catch (TimeoutException e) {
            status = "timeout";
            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("[RouterTools] {} 超时, 耗时={}ms, traceId={}, question={}",
                    toolName, costMs, traceId, truncate(question, 80));
            return toolName.contains("weather") ? "天气查询超时，请稍后重试" : "美食查询超时，请稍后重试";

        } catch (Exception e) {
            status = "error";
            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.error("[RouterTools] {} 异常, 耗时={}ms, traceId={}, question={}",
                    toolName, costMs, traceId, truncate(question, 80), e);
            return "查询失败: " + e.getMessage();

        } finally {
            recordMetrics(toolName, status, start);
        }
    }

    private void recordMetrics(String toolName, String status, long startNano) {
        Timer.builder("router.tool.duration")
                .tag("tool", toolName)
                .tag("status", status)
                .description("工具调用耗时")
                .register(meterRegistry)
                .record(System.nanoTime() - startNano, TimeUnit.NANOSECONDS);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @FunctionalInterface
    interface ToolTask {
        String execute() throws Exception;
    }
}
