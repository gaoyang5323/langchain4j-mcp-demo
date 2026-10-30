package com.kakuiwong.langchain4jdemo.controller;

import com.kakuiwong.langchain4jdemo.chat.WeatherChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@RestController
public class ChatController {

    private final WeatherChatService chatService;

    public ChatController(WeatherChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        return chatService.chat(msg);
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(@RequestParam String msg) {
        SseEmitter emitter = new SseEmitter(120_000L); // 超时 2 分钟
        // 异步执行，避免阻塞 Servlet 线程
        CompletableFuture.runAsync(() -> chatService.stream(msg, emitter));
        return emitter;
    }
}
