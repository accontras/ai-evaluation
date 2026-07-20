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
     * 发送聊天请求，返回文本回复。
     */
    public String chat(String systemPrompt, String userPrompt) {
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
            if (response.statusCode() != 200) {
                log.error("LLM call failed: status={}, body={}", response.statusCode(), response.body());
                throw new RuntimeException("LLM call failed: HTTP " + response.statusCode());
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");
            log.debug("LLM response: {} chars", content.length());
            return content;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed", e);
        }
    }

    /**
     * 发送聊天请求，返回结构化 JSON（用于 LLM-as-Judge 打分场景）。
     */
    public JSONObject chatForJson(String systemPrompt, String userPrompt) {
        String raw = chat(systemPrompt, userPrompt);
        // 提取 JSON 块（LLM 可能用 ```json 包裹）
        String jsonStr = raw;
        if (raw.contains("```json")) {
            jsonStr = raw.substring(raw.indexOf("```json") + 7, raw.lastIndexOf("```"));
        } else if (raw.contains("```")) {
            jsonStr = raw.substring(raw.indexOf("```") + 3, raw.lastIndexOf("```"));
        }
        try {
            return JSONUtil.parseObj(jsonStr.trim());
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response, raw: {}", raw);
            throw new RuntimeException("LLM did not return valid JSON", e);
        }
    }

    public String getModel() { return model; }
}
