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
 * DeepSeek API 客户端 — OpenAI 兼容接口。
 */
public class DeepSeekClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final HttpClient httpClient;

    public DeepSeekClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, 0.3);
    }

    public DeepSeekClient(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override
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
                log.error("DeepSeek call failed: status={}, body={}", response.statusCode(), response.body());
                return new LlmResponse(null, 0, 0, duration, "HTTP_" + response.statusCode());
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            String content = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getStr("content");

            JSONObject usage = json.getJSONObject("usage");
            int inputTokens = usage != null ? usage.getInt("prompt_tokens", 0) : 0;
            int outputTokens = usage != null ? usage.getInt("completion_tokens", 0) : 0;

            return new LlmResponse(content, inputTokens, outputTokens, duration, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            return new LlmResponse(null, 0, 0, System.currentTimeMillis() - start, e.getClass().getSimpleName());
        }
    }

    @Override
    public LlmJsonResponse chatForJson(String systemPrompt, String userPrompt) {
        var resp = chat(systemPrompt, userPrompt);
        if (resp.content() == null) return new LlmJsonResponse(null, resp);
        String raw = resp.content();
        String jsonStr = raw;
        if (raw.contains("```json")) jsonStr = raw.substring(raw.indexOf("```json") + 7, raw.lastIndexOf("```"));
        else if (raw.contains("```")) jsonStr = raw.substring(raw.indexOf("```") + 3, raw.lastIndexOf("```"));
        try {
            return new LlmJsonResponse(JSONUtil.parseObj(jsonStr.trim()), resp);
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response, raw: {}", raw);
            return new LlmJsonResponse(null, resp);
        }
    }

    @Override public String getModel() { return model; }
    @Override public double getTemperature() { return temperature; }
}
