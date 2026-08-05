package com.ranadvisor.orchestrator;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupervisorConfig {

    /**
     * The Gemini model is built in {@code com.ranadvisor.config.ChatModelConfig};
     * this class only wires the agent, its tools and its memory.
     */
    @Bean
    public RanSupervisorAgent ranSupervisorAgent(ChatModel chatModel, OrchestratorTools tools) {

        return AiServices.builder(RanSupervisorAgent.class)
                .chatModel(chatModel)
                .tools(tools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
