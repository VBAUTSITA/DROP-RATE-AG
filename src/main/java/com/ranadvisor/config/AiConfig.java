package com.ranadvisor.config;

import com.ranadvisor.agent.TelecomAgent;
import com.ranadvisor.agent.TelecomTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * The Gemini model is built in {@link ChatModelConfig}; this class only wires
     * the agent, its tools and its memory.
     */
    @Bean
    public TelecomAgent telecomAgent(ChatModel chatModel, TelecomTools tools) {

        return AiServices.builder(TelecomAgent.class)
                .chatModel(chatModel)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
