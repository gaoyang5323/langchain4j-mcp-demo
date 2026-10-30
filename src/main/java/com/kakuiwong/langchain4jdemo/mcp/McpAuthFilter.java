package com.kakuiwong.langchain4jdemo.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MCP Server 请求凭证校验过滤器。
 * 拦截所有发往 MCP 端点（/mcp）的请求，校验是否携带有效 token。
 * token 可通过请求参数 ?token=xxx 或请求头 Authorization: Bearer xxx 传递。
 */
@Component
@Order(1)
public class McpAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpAuthFilter.class);

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String VALID_TOKEN = "123";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null || !VALID_TOKEN.equals(token)) {
            log.warn("[McpAuthFilter] 凭证校验失败, uri={}, token={}", request.getRequestURI(), token);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"invalid or missing token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 仅拦截 MCP 端点请求
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(MCP_ENDPOINT);
    }

    /**
     * 从请求参数或 Authorization 头中提取 token。
     * 优先查询参数 ?token=xxx，其次 Header: Authorization: Bearer xxx
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. 查询参数
        String paramToken = request.getParameter("token");
        if (paramToken != null && !paramToken.isBlank()) {
            return paramToken.trim();
        }
        // 2. Authorization 头（Bearer 格式）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}
