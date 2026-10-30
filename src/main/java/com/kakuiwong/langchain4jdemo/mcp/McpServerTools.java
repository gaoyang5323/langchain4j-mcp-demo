package com.kakuiwong.langchain4jdemo.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 工具集，通过 @McpTool 注解将方法暴露为 MCP 工具，
 * 外部 MCP Client（如 Claude Desktop、Cursor 等）可直接发现并调用。
 */
@Slf4j
@Component
public class McpServerTools {

    @McpTool(
            name = "weather_query",
            description = "根据城市名称查询当前天气信息，返回温度、湿度、天气状况等"
    )
    public String queryWeather(
            @McpToolParam(description = "城市名称，例如：北京、上海", required = true) String city
    ) {
        log.info("queryWeather" + city);
        // 示例返回，实际项目中应调用真实天气服务
        return "城市：" + city + "，温度：25°C，湿度：60%，天气：晴";
    }

    @McpTool(
            name = "food_query",
            description = "根据城市名称查询当地特色美食推荐"
    )
    public String queryFood(
            @McpToolParam(description = "城市名称，例如：成都、广州", required = true) String city
    ) {
        log.info("queryFood" + city);
        // 示例返回，实际项目中应调用真实美食服务
        return city + "特色美食：火锅、串串香、龙抄手";
    }
}
