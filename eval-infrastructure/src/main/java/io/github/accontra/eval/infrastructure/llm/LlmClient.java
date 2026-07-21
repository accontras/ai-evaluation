package io.github.accontra.eval.infrastructure.llm;

import cn.hutool.json.JSONObject;

/**
 * LLM 客户端接口 — A1.1 多模型对比基础设施。
 *
 * 所有 LLM 提供商 (DeepSeek / GLM / Qwen / OpenAI) 实现此接口。
 * 扩展新模型只需加一个实现类 + 一行 Bean 配置。
 */
public interface LlmClient {

    /** 发送聊天请求，返回文本 + 可观测性数据。 */
    LlmResponse chat(String systemPrompt, String userPrompt);

    /** 发送聊天请求，返回结构化 JSON + 可观测性数据。 */
    LlmJsonResponse chatForJson(String systemPrompt, String userPrompt);

    String getModel();

    double getTemperature();

    /** LLM 调用响应 + 可观测性数据 */
    record LlmResponse(String content, int inputTokens, int outputTokens,
                       long durationMs, String errorType) {
        public boolean isError() { return errorType != null; }
        public int totalTokens() { return inputTokens + outputTokens; }
    }

    /** JSON 响应 + 可观测性数据 */
    record LlmJsonResponse(JSONObject json, LlmResponse metrics) {}
}
