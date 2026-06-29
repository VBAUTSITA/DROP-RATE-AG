package com.ranadvisor.logging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.io.FileWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/logs")
public class LogController {

    @Autowired
    private AgentLogRepository logRepo;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEP = "─".repeat(60);

    @GetMapping(value = "/recent", produces = "text/plain;charset=UTF-8")
    public String recent(@RequestParam(defaultValue = "50") int n) {
        List<AgentLog> logs = logRepo
            .findAll(PageRequest.of(0, n, Sort.by("createdAt").descending()))
            .getContent();
        return format(logs);
    }

    @GetMapping(value = "/export", produces = "text/plain;charset=UTF-8")
    public String export() throws Exception {
        List<AgentLog> logs = logRepo.findAll(Sort.by("createdAt").descending());
        String content = format(logs);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "agent_log_" + timestamp + ".txt";
        Path outPath = Path.of(System.getProperty("user.dir"), filename);
        try (FileWriter fw = new FileWriter(outPath.toFile())) {
            fw.write(content);
        }
        return "Exported " + logs.size() + " rows to " + filename;
    }

    private String format(List<AgentLog> logs) {
        if (logs.isEmpty()) return "No log entries yet.\n";
        StringBuilder sb = new StringBuilder();
        for (AgentLog entry : logs) {
            String ts = entry.getCreatedAt() != null
                ? entry.getCreatedAt().format(DISPLAY_FMT) : "unknown";
            sb.append(String.format("[%s] agent=%-8s tool=%-30s latency=%dms%n",
                ts, entry.getAgentName(), entry.getToolCalled(), entry.getLatencyMs()));
            sb.append("  INPUT:  ").append(entry.getToolInput()).append('\n');
            sb.append("  OUTPUT: ").append(entry.getToolOutput()).append('\n');
            sb.append(SEP).append('\n');
        }
        return sb.toString();
    }
}
