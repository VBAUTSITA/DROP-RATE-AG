package com.ranadvisor.config;

import com.ranadvisor.agent.TelecomAgent;
import com.ranadvisor.agent.TelecomTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public TelecomAgent telecomAgent(
            @Value("${openai.api-key}") String apiKey,
            TelecomTools tools) {

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://openrouter.ai/api/v1")
                .modelName("openai/gpt-4o-mini")
                .temperature(0.1) // low = more methodical tool use
                .build();

        return AiServices.builder(TelecomAgent.class)
                .chatLanguageModel(model)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
