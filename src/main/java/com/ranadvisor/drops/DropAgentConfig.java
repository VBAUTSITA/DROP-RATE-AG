package com.ranadvisor.drops;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DropAgentConfig {

    @Bean
    public DropRateAgent dropRateAgent(
            @Value("${openai.api-key}") String apiKey,
            DropAnalysisTool tools) {

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://openrouter.ai/api/v1")
                .modelName("openai/gpt-4o-mini")
                .temperature(0.1)
                .build();

        return AiServices.builder(DropRateAgent.class)
                .chatLanguageModel(model)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
