package com.kakuiwong.langchain4jdemo.chat;

import com.kakuiwong.langchain4jdemo.service.WebSearchService;
import com.kakuiwong.langchain4jdemo.tool.FoodTool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Service
public class FoodChatService {

    private final Assistant assistant;

    public FoodChatService(StreamingChatModel streamingChatModel,
                           ChatModel chatModel,
                           EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           WebSearchService webSearchService,
                           FoodTool foodTool) {


        // 向量库检索器
        ContentRetriever vectorRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        // RAG 增强器：向量库优先，无结果时回退到网络搜索
        DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever((query) -> {
                    var results = vectorRetriever.retrieve(query);
                    if (results.isEmpty()) {
                        // 向量库无答案，使用网络搜索
                        String searchResult = webSearchService.search(query.text());
                        return List.of(Content.from(searchResult));
                    }
                    // 返回向量库结果
                    return results.stream()
                            .map(retrieval -> Content.from(retrieval.textSegment()))
                            .toList();
                })
                .build();

        // 多轮对话记忆
        ChatMemoryProvider chatMemoryProvider = memoryId ->
                MessageWindowChatMemory.withMaxMessages(20);


        this.assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingChatModel)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                .tools(foodTool)
                .build();
    }

    public String chat(String message) {
        return assistant.chat("default", message);
    }

    public void stream(String message, SseEmitter emitter) {
        assistant.chatStream("default", message)
                .onPartialResponse(token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> {
                    emitter.complete();
                })
                .onError(emitter::completeWithError)
                .start();
    }

    public interface Assistant {
        String chat(@MemoryId String memoryId, @UserMessage String message);

        TokenStream chatStream(@MemoryId String memoryId, @UserMessage String message);
    }
}
