package com.kakuiwong.langchain4jdemo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WebSearchService {

    private final String baseUrl;
    private final HttpClient httpClient;

    public WebSearchService(@Value("${searxng.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public String search(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = baseUrl + "/search?q=" + encodedQuery + "&format=json";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return extractRelevantInfo(response.body());
            }
            return "未找到相关信息";
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    private String extractRelevantInfo(String jsonResponse) {
        StringBuilder result = new StringBuilder();
        result.append("网络搜索结果:\n\n");
        
        int maxResults = 3;
        int count = 0;
        
        int pos = 0;
        while (pos < jsonResponse.length() && count < maxResults) {
            int titleStart = jsonResponse.indexOf("\"title\":\"", pos);
            if (titleStart == -1) break;
            titleStart += 9;
            int titleEnd = jsonResponse.indexOf("\"", titleStart);
            String title = jsonResponse.substring(titleStart, titleEnd);
            
            int contentStart = jsonResponse.indexOf("\"content\":\"", titleEnd);
            if (contentStart == -1) break;
            contentStart += 11;
            int contentEnd = jsonResponse.indexOf("\"", contentStart);
            String content = jsonResponse.substring(contentStart, contentEnd);
            
            result.append(String.format("%d. %s\n   %s\n\n", count + 1, title, content));
            count++;
            pos = contentEnd;
        }
        
        return result.toString();
    }
}
