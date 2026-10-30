package com.kakuiwong.langchain4jdemo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 请求入口拦截器，为每个请求生成 traceId 并注入 MDC。
 * <p>
 * 响应头中返回 traceId，方便客户端关联日志排查。
 */
@Component
public class TraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.generateTraceId();
        }

        TraceContext.setTraceId(traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startTime = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - startTime) / 1_000_000;
            log.info("[TraceFilter] {} {} {}ms traceId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    costMs,
                    traceId);
            TraceContext.clear();
        }
    }
}
