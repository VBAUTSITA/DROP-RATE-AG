package com.ranadvisor.drops;

import com.ranadvisor.guardrail.InputGuardrail;
import com.ranadvisor.guardrail.InputGuardrail.GuardrailResult;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class DropAgentController {

    @Autowired
    private DropRateAgent dropRateAgent;

    @Autowired
    private InputGuardrail guardrail;

    @Autowired
    private AgentLogRepository logRepo;

    @GetMapping("/agent/drops")
    public String chat(@RequestParam String message) {
        GuardrailResult result = guardrail.check(message);

        if (!result.allowed()) {
            AgentLog entry = new AgentLog();
            entry.setAgentName("drops");
            entry.setToolCalled("GUARDRAIL_BLOCK");
            entry.setToolInput(message != null && message.length() > 500
                ? message.substring(0, 500) + "...[truncated]" : message);
            entry.setToolOutput("Blocked: " + result.reason());
            entry.setLatencyMs(0L);
            logRepo.save(entry);

            return "Lo siento, solo puedo ayudarte con análisis de caídas de llamadas " +
                   "y retenibilidad de la red 5G NSA. Por favor reformula tu pregunta " +
                   "sobre celdas, tasas de caída o causas de fallo.";
        }

        return dropRateAgent.chat(message);
    }
}
