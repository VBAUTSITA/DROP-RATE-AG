package com.ranadvisor.drops;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DropAgentController {

    @Autowired
    private DropRateAgent dropRateAgent;

    @GetMapping("/agent/drops")
    public String chat(@RequestParam String message) {
        return dropRateAgent.chat(message);
    }
}
