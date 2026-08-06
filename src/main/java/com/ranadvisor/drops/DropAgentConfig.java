package com.ranadvisor.drops;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DropAgentConfig {

    /**
     * The Gemini model is built in {@code com.ranadvisor.config.ChatModelConfig};
     * this class only wires the agent, its tools and its memory.
     */
    @Bean
    public DropRateAgent dropRateAgent(ChatModel chatModel, DropAnalysisTool tools) {

        return AiServices.builder(DropRateAgent.class)
                .chatModel(chatModel)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
