package com.ranadvisor.service;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatModel model;

    public ChatService(@Qualifier("freeChatModel") ChatModel model) {
        this.model = model;
    }

    public String chat(String message) {
        return model.chat(message);
    }
}
