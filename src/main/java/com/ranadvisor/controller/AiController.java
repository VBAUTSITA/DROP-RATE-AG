package com.ranadvisor.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ranadvisor.controller.dto.AskRequest;
import com.ranadvisor.controller.dto.AskResponse;
import com.ranadvisor.service.ChatService;
import com.ranadvisor.agent.TelecomAgent;

@RestController
public class AiController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private TelecomAgent telecomAgent;

    @GetMapping("/ai/chat")
    public String chat(@RequestParam String message) {
        return chatService.chat(message);
    }

    @PostMapping(
        value = "/agent/telecom",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AskResponse> agentTelecom(@RequestBody AskRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest()
                .body(new AskResponse("El campo 'message' no puede estar vacío."));
        }
        String answer = telecomAgent.chat(request.message());
        return ResponseEntity.ok(new AskResponse(answer));
    }
}
