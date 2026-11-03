package com.lotflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Spring AI 所需的 Jackson 2 序列化配置。 */
@Configuration
public class Jackson2Config {

    @Bean
    @Primary
    public ObjectMapper jackson2ObjectMapper() {
        // Spring AI 依赖 Jackson 2，而 Boot 4 Web 层使用 Jackson 3；这里只服务内部 AI/事件序列化。
        return new ObjectMapper().findAndRegisterModules();
    }
}
