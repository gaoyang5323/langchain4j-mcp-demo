package com.kakuiwong.langchain4jdemo.chat;

import com.kakuiwong.langchain4jdemo.config.TraceContext;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 路由聊天服务：基于 ReAct 模式，根据用户意图自动分发到 Weather 或 Food 子服务。
 * <p>
 * traceId 由 Controller 通过参数显式传入，通过 volatile 字段传递给 RouterTools。
 */
@Service
public class RouterChatService {

    private static final Logger log = LoggerFactory.getLogger(RouterChatService.class);

    private final RouterAssistant routerAssistant;
    private final RouterTools routerTools;
    private final MeterRegistry meterRegistry;

    public RouterChatService(StreamingChatModel streamingChatModel,
                             WeatherChatService weatherChatService,
                             FoodChatService foodChatService,
                             MeterRegistry meterRegistry,
                             @Qualifier("mdcTaskExecutor") Executor mdcTaskExecutor,
                             @Value("${router.tool-timeout-seconds:30}") long toolTimeoutSeconds) {

        this.meterRegistry = meterRegistry;

        ChatMemoryProvider chatMemoryProvider = memoryId ->
                MessageWindowChatMemory.withMaxMessages(20);

        this.routerTools = new RouterTools(
                weatherChatService, foodChatService, toolTimeoutSeconds, meterRegistry, mdcTaskExecutor);

        this.routerAssistant = AiServices.builder(RouterAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(routerTools)
                .build();

        log.info("[RouterChatService] 初始化完成, toolTimeout={}s", toolTimeoutSeconds);
    }

    /**
     * 同步对话
     *
     * @param traceId 由 Controller 从 HTTP 线程传入
     */
    public String chat(String memoryId, String message, String traceId) {
        long start = System.nanoTime();
        TraceContext.setTraceId(traceId);
        TraceContext.setSessionId(memoryId);

        // 将 traceId 设置到 RouterTools 的 volatile 字段，供 LangChain4j 内部线程读取
        routerTools.setCurrentTraceId(traceId);

        log.info("[RouterChatService] chat 请求, memoryId={}, traceId={}, message={}",
                memoryId, traceId, truncate(message, 100));
        try {
            String result = routerAssistant.chat(memoryId, message);
            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[RouterChatService] chat 完成, memoryId={}, traceId={}, 耗时={}ms",
                    memoryId, traceId, costMs);
            recordRequestMetrics("chat", "success", start);
            return result;
        } catch (Exception e) {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.error("[RouterChatService] chat 异常, memoryId={}, traceId={}, 耗时={}ms",
                    memoryId, traceId, costMs, e);
            recordRequestMetrics("chat", "error", start);
            return "抱歉，处理您的请求时遇到问题，请稍后重试。";
        }
    }

    /**
     * 流式对话（SSE）
     *
     * @param traceId 由 Controller 从 HTTP 线程传入
     */
    public void stream(String memoryId, String message, SseEmitter emitter, String traceId) {
        long start = System.nanoTime();
        TraceContext.setTraceId(traceId);
        TraceContext.setSessionId(memoryId);

        // 将 traceId 设置到 RouterTools 的 volatile 字段，供 LangChain4j 内部线程读取
        routerTools.setCurrentTraceId(traceId);

        log.info("[RouterChatService] stream 请求, memoryId={}, traceId={}, message={}",
                memoryId, traceId, truncate(message, 100));
        try {
            routerAssistant.chatStream(memoryId, message)
                    .onPartialResponse(token -> {
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            log.warn("[RouterChatService] SSE 发送失败, memoryId={}, traceId={}",
                                    memoryId, traceId, e);
                            emitter.completeWithError(e);
                        }
                    })
                    .onCompleteResponse(response -> {
                        long costMs = (System.nanoTime() - start) / 1_000_000;
                        log.info("[RouterChatService] stream 完成, memoryId={}, traceId={}, 耗时={}ms",
                                memoryId, traceId, costMs);
                        recordRequestMetrics("stream", "success", start);
                        emitter.complete();
                    })
                    .onError(error -> {
                        long costMs = (System.nanoTime() - start) / 1_000_000;
                        log.error("[RouterChatService] stream 异常, memoryId={}, traceId={}, 耗时={}ms",
                                memoryId, traceId, costMs, error);
                        recordRequestMetrics("stream", "error", start);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("处理请求时出现异常，请稍后重试"));
                        } catch (IOException ignored) {
                        }
                        emitter.completeWithError(error);
                    })
                    .start();
        } catch (Exception e) {
            log.error("[RouterChatService] stream 启动失败, memoryId={}, traceId={}",
                    memoryId, traceId, e);
            recordRequestMetrics("stream", "error", start);
            emitter.completeWithError(e);
        }
    }

    private void recordRequestMetrics(String type, String status, long startNano) {
        Timer.builder("router.request.duration")
                .tag("type", type)
                .tag("status", status)
                .description("路由请求总耗时")
                .register(meterRegistry)
                .record(System.nanoTime() - startNano, TimeUnit.NANOSECONDS);
    }

    public interface RouterAssistant {

        @SystemMessage("""
                你是一个智能路由助手，负责将用户的问题分配给合适的专家服务来处理。

                ## 可用工具
                - weatherQuery：天气专家，处理天气、气温、降雨、风力、空气质量等气象问题
                - foodQuery：美食专家，处理食物、菜谱、烹饪、饮食、特产、餐厅等美食问题

                ## 工作流程（ReAct 模式）
                1. 【分析意图】仔细阅读用户问题，判断涉及哪个领域
                2. 【选择工具】调用对应的工具获取专业回答
                3. 【评估结果】检查工具返回的内容是否充分回答了用户问题
                4. 【补充查询】如果问题涉及多个领域，可以调用多个工具
                5. 【综合回答】整合所有工具的结果，给出完整、准确的回答

                ## 路由规则
                - 天气相关 → weatherQuery
                - 美食相关 → foodQuery
                - 同时涉及天气和美食 → 两个工具都调用，然后综合回答
                - 不属于以上领域 → 用你自己的知识直接回答

                ## 注意事项
                - 优先使用工具获取信息，不要凭记忆回答事实性问题
                - 如果工具返回错误信息，如实告知用户并建议稍后重试
                - 回答时不要暴露内部工具调用细节
                """)
        String chat(@MemoryId String memoryId, @UserMessage String message);

        @SystemMessage("""
                你是一个智能路由助手，负责将用户的问题分配给合适的专家服务来处理。

                ## 可用工具
                - weatherQuery：天气专家，处理天气、气温、降雨、风力、空气质量等气象问题
                - foodQuery：美食专家，处理食物、菜谱、烹饪、饮食、特产、餐厅等美食问题

                ## 工作流程（ReAct 模式）
                1. 【分析意图】仔细阅读用户问题，判断涉及哪个领域
                2. 【选择工具】调用对应的工具获取专业回答
                3. 【评估结果】检查工具返回的内容是否充分回答了用户问题
                4. 【补充查询】如果问题涉及多个领域，可以调用多个工具
                5. 【综合回答】整合所有工具的结果，给出完整、准确的回答

                ## 路由规则
                - 天气相关 → weatherQuery
                - 美食相关 → foodQuery
                - 同时涉及天气和美食 → 两个工具都调用，然后综合回答
                - 不属于以上领域 → 用你自己的知识直接回答

                ## 注意事项
                - 优先使用工具获取信息，不要凭记忆回答事实性问题
                - 如果工具返回错误信息，如实告知用户并建议稍后重试
                - 回答时不要暴露内部工具调用细节
                """)
        TokenStream chatStream(@MemoryId String memoryId, @UserMessage String message);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
