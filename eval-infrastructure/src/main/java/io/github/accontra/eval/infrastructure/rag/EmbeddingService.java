package io.github.accontra.eval.infrastructure.rag;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A3 RAG: 文本向量化服务 — bge-small-zh-v1.5 ONNX 模型。
 *
 * 将输入文本编码为 512 维 L2 归一化向量。
 * 使用 ONNX Runtime Java API 直接推理，HuggingFace tokenizer 做分词。
 */
@Component
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    // 多路径兜底: CWD=项目根(restart.sh) / CWD=boot(IDE) / 绝对路径
    private static final Path[] MODEL_DIR_CANDIDATES = {
        Path.of("eval-infrastructure/src/main/resources/rag"),
        Path.of("../eval-infrastructure/src/main/resources/rag"),
        Path.of("e:/working-brain/eval-system/eval-infrastructure/src/main/resources/rag")
    };
    private static final int DIM = 512;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        // 多路径兜底: 找到第一个存在 model.onnx 的目录
        Path modelDir = null;
        for (Path candidate : MODEL_DIR_CANDIDATES) {
            if (Files.exists(candidate.resolve("model.onnx"))) {
                modelDir = candidate;
                break;
            }
        }
        if (modelDir == null) {
            log.warn("[RAG] 模型文件缺失, 搜索路径: {}, RAG 不可用",
                    java.util.Arrays.toString(MODEL_DIR_CANDIDATES));
            available = false;
            return;
        }

        Path modelPath = modelDir.resolve("model.onnx");
        Path tokenizerPath = modelDir.resolve("tokenizer.json");

        if (!Files.exists(tokenizerPath)) {
            log.warn("[RAG] tokenizer 缺失: {}, RAG 不可用", tokenizerPath);
            available = false;
            return;
        }

        try {
            long start = System.currentTimeMillis();

            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
            this.available = true;

            long elapsed = System.currentTimeMillis() - start;
            log.info("[RAG] bge-small-zh-v1.5 ONNX 加载完成, 耗时={}ms", elapsed);
        } catch (Exception e) {
            log.error("[RAG] 模型加载失败, RAG 不可用", e);
            available = false;
        }
    }

    @PreDestroy
    public void destroy() {
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (env != null) env.close(); } catch (Exception ignored) {}
    }

    public boolean isAvailable() { return available; }

    /**
     * 将文本编码为 512 维 L2 归一化向量。
     */
    public float[] encode(String text) {
        if (!available) return new float[DIM];

        String input = "为这个句子生成表示以用于检索相关文章：" + text;

        try {
            var encoding = tokenizer.encode(input);
            long[] tokenIds = encoding.getIds();
            int seqLen = tokenIds.length;

            // 构建 input_ids, attention_mask, token_type_ids
            long[] attentionMask = new long[seqLen];
            long[] tokenTypeIds = new long[seqLen];
            java.util.Arrays.fill(attentionMask, 1L);

            long[] shape = {1, seqLen};

            try (OnnxTensor inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenIds), shape);
                 OnnxTensor attMask = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);
                 OnnxTensor typeIds = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)) {

                Map<String, OnnxTensor> inputs = Map.of(
                        "input_ids", inputIds,
                        "attention_mask", attMask,
                        "token_type_ids", typeIds);

                try (OrtSession.Result result = session.run(inputs)) {
                    // output: last_hidden_state [1, seqLen, 512]
                    float[][][] hidden = (float[][][]) result.get(0).getValue();

                    // Mean pooling + L2 normalize
                    return meanPoolNormalize(hidden[0], attentionMask);
                }
            }
        } catch (Exception e) {
            log.warn("[RAG] encode 失败: {}", e.getMessage());
            return new float[DIM];
        }
    }

    /** Mean pooling: 对非 padding 位置取平均, 然后 L2 归一化 */
    private float[] meanPoolNormalize(float[][] tokenEmbeddings, long[] attentionMask) {
        int seqLen = tokenEmbeddings.length;
        float[] pooled = new float[DIM];
        int count = 0;
        for (int i = 0; i < seqLen; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < DIM; j++) {
                    pooled[j] += tokenEmbeddings[i][j];
                }
                count++;
            }
        }
        // L2 normalize
        float norm = 0f;
        for (int j = 0; j < DIM; j++) {
            pooled[j] /= count;
            norm += pooled[j] * pooled[j];
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int j = 0; j < DIM; j++) {
                pooled[j] /= norm;
            }
        }
        return pooled;
    }
}
