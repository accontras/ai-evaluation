package io.github.accontra.eval.infrastructure.llm;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM 客户端 — 支持 OpenAI 兼容接口 (DeepSeek / OpenAI / 本地模型)。
 */
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final HttpClient httpClient;

    public LlmClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, 0.3);
    }

    public LlmClient(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 发送聊天请求，返回文本 + 可观测性数据。
     */
    public LlmResponse chat(String systemPrompt, String userPrompt) {
        long start = System.currentTimeMillis();
        try {
            JSONObject body = new JSONObject();
            body.set("model", model);
            body.set("temperature", temperature);
            body.set("max_tokens", 2048);

            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
            messages.add(new JSONObject().set("role", "user").set("content", userPrompt));
            body.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                log.error("LLM call failed: status={}, body={}", response.statusCode(), response.body());
                return new LlmResponse(null, 0, 0, duration, "HTTP_" + response.statusCode());
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");

            JSONObject usage = json.getJSONObject("usage");
            int inputTokens = usage != null ? usage.getInt("prompt_tokens", 0) : 0;
            int outputTokens = usage != null ? usage.getInt("completion_tokens", 0) : 0;

            log.debug("LLM response: {} chars, {}ms, {} tokens (in:{}, out:{})",
                    content.length(), duration, inputTokens + outputTokens, inputTokens, outputTokens);
            return new LlmResponse(content, inputTokens, outputTokens, duration, null);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new LlmResponse(null, 0, 0, duration, e.getClass().getSimpleName());
        }
    }

    /**
     * 发送聊天请求，返回结构化 JSON + 可观测性数据。
     */
    public LlmJsonResponse chatForJson(String systemPrompt, String userPrompt) {
        var resp = chat(systemPrompt, userPrompt);
        if (resp.content == null) {
            return new LlmJsonResponse(null, resp);
        }
        String raw = resp.content;
        // 提取 JSON 块（LLM 可能用 ```json 包裹）
        String jsonStr = raw;
        if (raw.contains("```json")) {
            jsonStr = raw.substring(raw.indexOf("```json") + 7, raw.lastIndexOf("```"));
        } else if (raw.contains("```")) {
            jsonStr = raw.substring(raw.indexOf("```") + 3, raw.lastIndexOf("```"));
        }
        try {
            return new LlmJsonResponse(JSONUtil.parseObj(jsonStr.trim()), resp);
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response, raw: {}", raw);
            return new LlmJsonResponse(null, resp);
        }
    }

    public String getModel() { return model; }
    public double getTemperature() { return temperature; }

    /** LLM 调用响应 + 可观测性数据 */
    public record LlmResponse(String content, int inputTokens, int outputTokens,
                               long durationMs, String errorType) {
        public boolean isError() { return errorType != null; }
        public int totalTokens() { return inputTokens + outputTokens; }
    }

    /** JSON 响应 + 可观测性数据 */
    public record LlmJsonResponse(JSONObject json, LlmResponse metrics) {}
}
