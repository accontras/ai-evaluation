package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<String> health() {
        return Result.ok("eval-system is running");
    }
}
