package com.ranadvisor.pci.planner;

import com.ranadvisor.pci.PciConflict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Backend that reaches the real multi-technology PCI/RSI planner over HTTP.
 *
 * <p>The two applications cannot share a JVM — the planner's assignment engine runs
 * JavaScript through Nashorn and therefore requires JDK 8, while this agent runs on
 * Java 17 — so an in-process call was never an option. HTTP is the seam.
 *
 * <p>Enable with {@code pci.planner.backend=rest} plus {@code pci.planner.base-url}.
 * Expected surface on the planner side (see {@code integration/tracker} for a reference
 * implementation that wraps the existing service without touching the engine):
 * <pre>
 *   GET  {base}/api/pci/plan/{cellName}    → current PCI/RSI of a live cell
 *   GET  {base}/api/pci/audit/{cellName}   → conflicts affecting that cell
 *   GET  {base}/api/pci/audit              → network-wide conflicts
 *   POST {base}/api/pci/propose            → {"cellName": "..."} → dry-run re-plan
 * </pre>
 *
 * <p>There is intentionally no call to any endpoint that persists a PCI. The planner's
 * own screen keeps that step, with a person in front of it.
 */
@Component
@ConditionalOnProperty(name = "pci.planner.backend", havingValue = "rest")
public class RestPciPlannerAdapter implements PciPlannerPort {

    static final String SOURCE = "planning-engine";

    private final RestClient http;
    private final String baseUrl;

    public RestPciPlannerAdapter(
            @Value("${pci.planner.base-url}") String baseUrl,
            @Value("${pci.planner.api-key:}") String apiKey,
            @Value("${pci.planner.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${pci.planner.read-timeout-ms:30000}") int readTimeoutMs) {

        this.baseUrl = baseUrl;

        // The re-plan call runs the assignment engine over the surrounding network, which
        // takes seconds — hence a read timeout far longer than the connect timeout.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        if (!apiKey.isBlank()) {
            builder = builder.defaultHeader("X-Api-Key", apiKey);
        }
        this.http = builder.build();
    }

    @Override
    public String backendName() {
        return "PCI/RSI planning engine at " + baseUrl;
    }

    @Override
    public CellPlan plan(String cellName) {
        PlanResponse r = get("/api/pci/plan/" + enc(cellName), PlanResponse.class, cellName);
        if (r == null) return CellPlan.notFound(cellName);
        return new CellPlan(r.cellname(), r.site(), r.pci(), r.rsi(), r.carrier(), r.azimuth(), true);
    }

    @Override
    public PciAudit audit(String cellName) {
        ConflictResponse[] rows = get("/api/pci/audit/" + enc(cellName), ConflictResponse[].class, cellName);
        return new PciAudit(cellName, toConflicts(rows), SOURCE);
    }

    @Override
    public PciAudit auditNetwork() {
        ConflictResponse[] rows = get("/api/pci/audit", ConflictResponse[].class, "(network)");
        return new PciAudit("(network)", toConflicts(rows), SOURCE);
    }

    @Override
    public PciProposal propose(String cellName) {
        ProposalResponse r;
        try {
            r = http.post()
                    .uri("/api/pci/propose")
                    .body(Map.of("cellName", cellName))
                    .retrieve()
                    .body(ProposalResponse.class);
        } catch (RestClientException e) {
            throw new PciPlannerUnavailableException(
                    "could not reach the planning engine to re-plan " + cellName + ": " + e.getMessage(), e);
        }
        if (r == null) {
            return PciProposal.unavailable(cellName, null,
                    "The planning engine returned an empty response.", SOURCE);
        }
        if (r.error() != null && !r.error().isBlank()) {
            return PciProposal.unavailable(cellName, r.currentPci(),
                    "The planning engine reported: " + r.error(), SOURCE);
        }
        return new PciProposal(
                cellName,
                r.currentPci(),
                r.pci(),
                r.rsi(),
                r.auditoria() == null ? List.of() : r.auditoria(),
                r.advertencias() == null ? List.of() : r.advertencias(),
                SOURCE);
    }

    private <T> T get(String path, Class<T> type, String subject) {
        try {
            return http.get().uri(path).retrieve().body(type);
        } catch (RestClientException e) {
            throw new PciPlannerUnavailableException(
                    "could not reach the planning engine for " + subject + ": " + e.getMessage(), e);
        }
    }

    private static List<PciConflict> toConflicts(ConflictResponse[] rows) {
        List<PciConflict> out = new ArrayList<>();
        if (rows == null) return out;
        for (ConflictResponse r : rows) {
            out.add(new PciConflict(r.type(), r.cellA(), r.pciA(), r.cellB(), r.pciB(),
                    r.note() == null ? "" : r.note()));
        }
        return out;
    }

    /** Cell names are alphanumeric with underscores, but encode anyway rather than assume. */
    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── wire shapes (mirror the reference controller in integration/tracker) ────

    record PlanResponse(String cellname, String site, Integer pci, Integer rsi,
                        String carrier, Integer azimuth) {}

    record ConflictResponse(String type, String cellA, Integer pciA,
                            String cellB, Integer pciB, String note) {}

    /** Field names follow the engine's own result shape so the planner side stays a thin pass-through. */
    record ProposalResponse(Integer currentPci, Integer pci, Integer rsi,
                            List<String> auditoria, List<String> advertencias, String error) {}
}
