package com.kakuiwong.langchain4jdemo.controller;

import com.kakuiwong.langchain4jdemo.chat.RouterChatService;
import com.kakuiwong.langchain4jdemo.config.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 路由聊天控制器。
 * <p>
 * traceId 由 TraceFilter 注入 MDC，在 Controller 中取出后通过参数显式传递到 Service 层。
 */
@RestController
@RequestMapping("/router")
public class RouterController {

    private static final Logger log = LoggerFactory.getLogger(RouterController.class);

    private final RouterChatService routerChatService;
    private final Executor mdcTaskExecutor;

    public RouterController(RouterChatService routerChatService,
                            @Qualifier("mdcTaskExecutor") Executor mdcTaskExecutor) {
        this.routerChatService = routerChatService;
        this.mdcTaskExecutor = mdcTaskExecutor;
    }

    @GetMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestParam(required = false) String msg,
            @RequestParam(defaultValue = "default") String sessionId) {

        if (msg == null || msg.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "参数 msg 不能为空"));
        }

        String traceId = TraceContext.getTraceId();
        log.info("[RouterController] /chat 请求, sessionId={}, traceId={}, msg={}",
                sessionId, traceId, truncate(msg, 80));

        try {
            String result = routerChatService.chat(sessionId, msg, traceId);
            return ResponseEntity.ok(Map.of(
                    "sessionId", sessionId,
                    "traceId", traceId,
                    "reply", result
            ));
        } catch (Exception e) {
            log.error("[RouterController] /chat 处理异常, sessionId={}, traceId={}",
                    sessionId, traceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "服务内部错误，请稍后重试", "traceId", traceId));
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(required = false) String msg,
            @RequestParam(defaultValue = "default") String sessionId) {

        if (msg == null || msg.isBlank()) {
            SseEmitter errorEmitter = new SseEmitter(0L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("参数 msg 不能为空"));
                errorEmitter.complete();
            } catch (Exception ignored) {
            }
            return errorEmitter;
        }

        String traceId = TraceContext.getTraceId();
        log.info("[RouterController] /stream 请求, sessionId={}, traceId={}, msg={}",
                sessionId, traceId, truncate(msg, 80));

        SseEmitter emitter = new SseEmitter(120_000L);

        emitter.onTimeout(() -> {
            log.warn("[RouterController] SSE 超时, sessionId={}, traceId={}", sessionId, traceId);
            emitter.complete();
        });
        emitter.onCompletion(() ->
                log.info("[RouterController] SSE 完成, sessionId={}, traceId={}", sessionId, traceId));

        // traceId 通过参数显式传递，不依赖 MDC 跨线程传播
        CompletableFuture.runAsync(() -> {
            try {
                routerChatService.stream(sessionId, msg, emitter, traceId);
            } catch (Exception e) {
                log.error("[RouterController] stream 异步执行异常, sessionId={}, traceId={}",
                        sessionId, traceId, e);
                emitter.completeWithError(e);
            }
        }, mdcTaskExecutor);

        return emitter;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
