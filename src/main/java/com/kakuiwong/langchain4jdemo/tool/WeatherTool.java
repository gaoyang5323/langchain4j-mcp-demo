package com.kakuiwong.langchain4jdemo.tool;

import com.kakuiwong.langchain4jdemo.config.TraceContext;
import com.kakuiwong.langchain4jdemo.service.WebSearchService;
import dev.langchain4j.agent.tool.Tool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 天气查询工具，通过网络搜索获取实时天气信息。
 */
@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    @Autowired
    WebSearchService webSearchService;

    @Autowired
    MeterRegistry meterRegistry;

    @Tool("根据城市名称查询当前天气信息，包括温度、湿度、风速等。参数为中文或英文城市名，例如：北京、上海、London")
    public String getWeather(String city) {
        long start = System.nanoTime();
        log.info("[WeatherTool] getWeather 开始执行, city={}, traceId={}",
                city, TraceContext.getTraceId());
        try {
            String result = webSearchService.search(city + "当前的天气");
            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.info("[WeatherTool] getWeather 执行成功, city={}, 耗时={}ms, traceId={}",
                    city, costMs, TraceContext.getTraceId());
            Timer.builder("tool.duration")
                    .tag("tool", "weather")
                    .tag("status", "success")
                    .register(meterRegistry)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (Exception e) {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            log.error("[WeatherTool] getWeather 异常, city={}, 耗时={}ms, traceId={}",
                    city, costMs, TraceContext.getTraceId(), e);
            Timer.builder("tool.duration")
                    .tag("tool", "weather")
                    .tag("status", "error")
                    .register(meterRegistry)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return "天气查询失败: " + e.getMessage();
        }
    }
}
