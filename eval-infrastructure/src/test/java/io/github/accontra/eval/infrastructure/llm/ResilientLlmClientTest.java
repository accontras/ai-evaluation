package io.github.accontra.eval.infrastructure.llm;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ResilientLlmClientTest {

    /** 始终成功的客户端 */
    private static LlmClient successClient(String model) {
        return new LlmClient() {
            @Override public LlmResponse chat(String sp, String up) {
                return new LlmResponse("ok", 10, 5, 100, null);
            }
            @Override public LlmJsonResponse chatForJson(String sp, String up) {
                return new LlmJsonResponse(null, new LlmResponse("{}", 10, 5, 100, null));
            }
            @Override public String getModel() { return model; }
            @Override public double getTemperature() { return 0.3; }
        };
    }

    /** 始终失败的客户端 */
    private static LlmClient failClient(String model) {
        return new LlmClient() {
            @Override public LlmResponse chat(String sp, String up) {
                return new LlmResponse(null, 0, 0, 0, "TEST_ERROR");
            }
            @Override public LlmJsonResponse chatForJson(String sp, String up) {
                return new LlmJsonResponse(null, new LlmResponse(null, 0, 0, 0, "TEST_ERROR"));
            }
            @Override public String getModel() { return model; }
            @Override public double getTemperature() { return 0.3; }
        };
    }

    @Test
    void primarySuccess() {
        var client = new ResilientLlmClient(successClient("deepseek"), List.of(), 1, 5, 30000, 8000);
        var resp = client.chat("hi", "hello");
        assertFalse(resp.isError());
        assertEquals("NONE", client.getLastDegradationLevel());
    }

    @Test
    void fallbackTriggered() {
        var fallback = successClient("glm");
        var client = new ResilientLlmClient(failClient("deepseek"), List.of(fallback), 0, 5, 30000, 8000);
        var resp = client.chat("hi", "hello");
        assertFalse(resp.isError());
        assertEquals("L1_FALLBACK", client.getLastDegradationLevel());
    }

    @Test
    void allModelsFailDegrades() {
        var client = new ResilientLlmClient(failClient("deepseek"),
                List.of(failClient("glm")), 0, 5, 30000, 8000);
        var resp = client.chat("hi", "hello");
        assertTrue(resp.isError());
        assertEquals("ALL_MODELS_FAILED", resp.errorType());
        assertEquals("L2_RULE", client.getLastDegradationLevel());
    }

    @Test
    void circuitTripsAfterThreshold() {
        var client = new ResilientLlmClient(failClient("deepseek"),
                List.of(failClient("glm")), 0, 2, 30000, 8000);
        // 第1次: failure 1
        client.chat("hi", "hello");
        assertEquals("L2_RULE", client.getLastDegradationLevel());
        // 第2次: failure 2 = threshold → circuit trips
        client.chat("hi", "hello");
        var status = client.getStatus();
        var circuit = (Map<String, Object>) status.get("circuit");
        assertTrue((Boolean) circuit.get("open"), "circuit should be open after threshold failures");
    }

    @Test
    void tokenBudgetExceeded() {
        var client = new ResilientLlmClient(successClient("deepseek"), List.of(), 0, 5, 30000, 2);
        // 第一次调用: tokenUsed=15, budget=2 → 超限
        client.chat("hi", "hello"); // 10 input + 5 output = 15 > 2 budget
        // 第二次: 预算已超
        var resp = client.chat("hi", "hello");
        assertTrue(resp.isError());
        assertEquals("TOKEN_BUDGET_EXCEEDED", resp.errorType());
        assertEquals("L3_DEFAULT", client.getLastDegradationLevel());
    }

    @Test
    void getStatusReturnsAllSections() {
        var client = new ResilientLlmClient(successClient("deepseek"),
                List.of(successClient("glm")), 1, 5, 30000, 8000);
        client.chat("hi", "hello");
        var status = client.getStatus();
        assertTrue(status.containsKey("circuit"));
        assertTrue(status.containsKey("primary"));
        assertTrue(status.containsKey("fallbacks"));
        assertTrue(status.containsKey("degradation"));
        assertTrue(status.containsKey("tokenBudget"));
    }
}
