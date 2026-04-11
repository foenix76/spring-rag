package com.example.korrag.config;

import com.example.korrag.service.HrActionTools;
import com.example.korrag.service.HrNavigationTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider hrToolCallbackProvider(HrNavigationTools navTools, HrActionTools actionTools) {
        // HrNavigationTools와 HrActionTools 내의 @Tool 어노테이션이 붙은 메소드들을 
        // MCP 서버가 인식할 수 있도록 ToolCallbackProvider로 변환하여 빈으로 등록합니다.
        return MethodToolCallbackProvider.builder()
                .toolObjects(navTools, actionTools)
                .build();
    }
}
