package io.github.accontra.eval.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 回调通知服务 — 异步 HTTP POST 通知评估完成。
 *
 * MVP: 简单 POST JSON 到配置的 callback URL。
 * S24: 模板化增强 (Header 认证、多轮重试、熔断)
 */
@Component
public class CallbackNotifyService {

    private static final Logger log = LoggerFactory.getLogger(CallbackNotifyService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 异步发送回调通知。
     */
    public void notifyAsync(String callbackUrl, Map<String, Object> payload) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.debug("[Callback] No callback URL, skipping");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String body = cn.hutool.json.JSONUtil.toJsonStr(payload);
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(callbackUrl))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("[Callback] OK: url={}, status={}", callbackUrl, response.statusCode());
            } catch (Exception e) {
                log.error("[Callback] Failed: url={}, err={}", callbackUrl, e.getMessage());
            }
        });
    }
}
