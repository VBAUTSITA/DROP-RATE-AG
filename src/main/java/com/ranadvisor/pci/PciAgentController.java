package com.ranadvisor.pci;

import com.ranadvisor.guardrail.GuardrailResult;
import com.ranadvisor.guardrail.InputGuardrail;
import com.ranadvisor.logging.AgentLog;
import com.ranadvisor.logging.AgentLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Standalone entry point for the PCI specialist agent. Mirrors {@code DropAgentController}
 * but uses only the deterministic {@link InputGuardrail} — the existing LLM guardrail is
 * scoped to drop-analysis topics, so a PCI-aware semantic guardrail is left as future work.
 */
@RestController
public class PciAgentController {

    private static final String CANNED_RESPONSE =
        "Lo siento, solo puedo ayudarte con planificación y conflictos de PCI " +
        "(Physical Cell Identity) de la red 5G: PCI de una celda, colisiones, " +
        "confusiones, conflictos mod-3, o auditorías de PCI de la red.";

    @Autowired private PciPlanningAgent   pciPlanningAgent;
    @Autowired private InputGuardrail     inputGuardrail;
    @Autowired private AgentLogRepository logRepo;

    @GetMapping("/agent/pci")
    public String chat(@RequestParam String message) {
        GuardrailResult fast = inputGuardrail.check(message);
        if (!fast.allowed()) {
            saveBlock(message, fast.reason());
            return CANNED_RESPONSE;
        }
        return pciPlanningAgent.chat(message);
    }

    private void saveBlock(String message, String reason) {
        AgentLog entry = new AgentLog();
        entry.setAgentName("pci");
        entry.setToolCalled("GUARDRAIL_BLOCK");
        entry.setToolInput(message != null && message.length() > 500
            ? message.substring(0, 500) + "...[truncated]" : message);
        entry.setToolOutput("Blocked: " + reason);
        entry.setLatencyMs(0L);
        logRepo.save(entry);
    }
}
