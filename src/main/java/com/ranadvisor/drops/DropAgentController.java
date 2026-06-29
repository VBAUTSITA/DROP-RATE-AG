package com.ranadvisor.drops;

import com.ranadvisor.guardrail.GuardrailResult;
import com.ranadvisor.guardrail.InputGuardrail;
import com.ranadvisor.guardrail.LlmGuardrail;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class DropAgentController {

    private static final String CANNED_RESPONSE =
        "Lo siento, solo puedo ayudarte con análisis de caídas de llamadas " +
        "y retenibilidad de la red 5G NSA. Por favor reformula tu pregunta " +
        "sobre celdas, tasas de caída o causas de fallo.";

    @Autowired private DropRateAgent      dropRateAgent;
    @Autowired private InputGuardrail     inputGuardrail;
    @Autowired private LlmGuardrail       llmGuardrail;
    @Autowired private AgentLogRepository logRepo;

    @GetMapping("/agent/drops")
    public String chat(@RequestParam String message) {

        // ── Layer 1: fast deterministic check (free, no API call) ─────────────
        GuardrailResult fast = inputGuardrail.check(message);
        if (!fast.allowed()) {
            saveBlock(message, "GUARDRAIL_BLOCK", fast.reason());
            return CANNED_RESPONSE;
        }

        // ── Layer 2: LLM semantic check (catches nuanced off-topic/unsafe) ─────
        GuardrailResult semantic = llmGuardrail.check(message);
        if (!semantic.allowed()) {
            saveBlock(message, "LLM_GUARDRAIL_BLOCK", semantic.reason());
            return CANNED_RESPONSE;
        }

        // ── Both layers passed: forward to main agent ──────────────────────────
        return dropRateAgent.chat(message);
    }

    private void saveBlock(String message, String toolCalled, String reason) {
        AgentLog entry = new AgentLog();
        entry.setAgentName("drops");
        entry.setToolCalled(toolCalled);
        entry.setToolInput(message != null && message.length() > 500
            ? message.substring(0, 500) + "...[truncated]" : message);
        entry.setToolOutput("Blocked: " + reason);
        entry.setLatencyMs(0L);
        logRepo.save(entry);
    }
}
