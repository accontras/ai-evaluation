package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.infrastructure.llm.ResilientLlmClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A4: AI 可靠性状态查询 API。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class ResilienceController {

    private final ResilientLlmClient resilientClient;

    public ResilienceController(ResilientLlmClient resilientClient) {
        this.resilientClient = resilientClient;
    }

    @GetMapping("/resilience-status")
    public Map<String, Object> status() {
        return resilientClient.getStatus();
    }
}
