package com.ranadvisor.guardrail;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class InputGuardrail {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 1000;

    private static final List<String> INJECTION_PHRASES = List.of(
        "ignore previous",
        "ignore all previous",
        "ignore your instructions",
        "disregard the above",
        "system prompt",
        "you are now",
        "forget your instructions",
        "act as",
        "pretend you are",
        "reveal your prompt",
        "print your instructions"
    );

    private static final Set<String> TELECOM_KEYWORDS = Set.of(
        // Spanish
        "celda", "celdas", "caida", "caidas", "drop", "drops",
        "red", "cobertura", "peor", "peores", "analiza", "analizar",
        "retenibilidad", "rate", "tasa", "problema", "problemas",
        "causa", "rendimiento", "kpi", "lista",
        // English
        "cell", "cells", "network", "coverage", "worst",
        "analyze", "retainability", "cause", "performance", "list",
        // PCI planning + multi-module orchestration
        "pci", "conflicto", "conflict", "colision", "collision",
        "confusion", "mod3", "mod-3", "handover", "traspaso",
        "vecino", "vecinos", "neighbor", "neighbour", "planificacion",
        "planning", "interferencia", "interference", "diagnostico",
        "diagnose", "raiz", "root", "orquesta", "supervisor",
        "modulo", "module"
    );

    // Matches cell-name-like tokens: 2+ uppercase letters followed by 3+ digits (e.g. ARR40312C1)
    private static final Pattern CELL_NAME_PATTERN = Pattern.compile("[A-Z]{2,}\\d{3,}");

    public GuardrailResult check(String userMessage) {

        // Check A — empty or too long
        if (userMessage == null || userMessage.isBlank() || userMessage.length() < MIN_LENGTH) {
            return new GuardrailResult(false, "empty_or_too_short");
        }
        if (userMessage.length() > MAX_LENGTH) {
            return new GuardrailResult(false, "too_long");
        }

        // Check B — prompt injection
        String lower = userMessage.toLowerCase();
        for (String phrase : INJECTION_PHRASES) {
            if (lower.contains(phrase)) {
                return new GuardrailResult(false, "prompt_injection");
            }
        }

        // Check C — on-topic relevance
        for (String keyword : TELECOM_KEYWORDS) {
            if (lower.contains(keyword)) {
                return new GuardrailResult(true, "ok");
            }
        }
        if (CELL_NAME_PATTERN.matcher(userMessage).find()) {
            return new GuardrailResult(true, "ok");
        }

        return new GuardrailResult(false, "off_topic");
    }
}
