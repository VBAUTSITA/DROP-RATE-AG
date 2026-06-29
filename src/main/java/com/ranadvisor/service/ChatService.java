package com.ranadvisor.service;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final OpenAiChatModel model;

    public ChatService(@Value("${openai.api-key}") String apiKey) {
        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://openrouter.ai/api/v1")
                .modelName("openai/gpt-4o-mini")
                .temperature(0.7)
                .build();
    }

    public String chat(String message) {
        return model.generate(message);
    }
}
