package io.github.accontra.eval.infrastructure.rag;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.onnxruntime.engine.OrtEngine;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A3 RAG: 文本向量化服务 — bge-small-zh ONNX 模型。
 *
 * 将输入文本编码为 512 维 L2 归一化向量。
 * 模型文件需手动下载到 resources/rag/ 目录。
 */
@Component
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final Path MODEL_DIR = Path.of("eval-infrastructure/src/main/resources/rag");
    private static final int DIM = 512;

    private ZooModel<NDList, NDList> model;
    private HuggingFaceTokenizer tokenizer;
    private NDManager manager;
    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        Path modelPath = MODEL_DIR.resolve("model.onnx");
        Path tokenizerPath = MODEL_DIR.resolve("tokenizer.json");

        if (!Files.exists(modelPath) || !Files.exists(tokenizerPath)) {
            log.warn("[RAG] 模型文件缺失: {} / {}, RAG 不可用，将降级到规则检索",
                    modelPath, tokenizerPath);
            available = false;
            return;
        }

        try {
            long start = System.currentTimeMillis();

            this.manager = NDManager.newBaseManager(new OrtEngine());

            var criteria = Criteria.builder()
                    .setTypes(NDList.class, NDList.class)
                    .optModelPath(modelPath.toAbsolutePath())
                    .optEngine("OnnxRuntime")
                    .build();
            this.model = ModelZoo.loadModel(criteria);
            this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
            this.available = true;

            long elapsed = System.currentTimeMillis() - start;
            log.info("[RAG] bge-small-zh 模型加载完成, 耗时={}ms", elapsed);
        } catch (Exception e) {
            log.error("[RAG] 模型加载失败, RAG 不可用", e);
            available = false;
        }
    }

    @PreDestroy
    public void destroy() {
        if (manager != null) manager.close();
        if (model != null) model.close();
    }

    public boolean isAvailable() { return available; }

    /**
     * 将文本编码为 512 维 L2 归一化向量。
     * bge 系列推荐为非对称检索添加 instruction prefix。
     */
    public float[] encode(String text) {
        if (!available) return new float[DIM];

        // bge 官方推荐的 instruction prefix
        String input = "为这个句子生成表示以用于检索相关文章：" + text;

        long[] tokenIds = tokenizer.encode(input).getIds();
        long[] attentionMask = new long[tokenIds.length];
        java.util.Arrays.fill(attentionMask, 1L);

        try (NDManager sub = manager.newSubManager()) {
            NDArray inputIds = sub.create(tokenIds);
            NDArray attMask = sub.create(attentionMask);
            NDList inputs = new NDList(inputIds.expandDims(0), attMask.expandDims(0));

            try (Predictor<NDList, NDList> predictor = model.newPredictor()) {
                NDList output = predictor.predict(inputs);
                NDArray lastHidden = output.get(0); // [1, seq_len, 512]

                // Mean pooling: 对非 padding 位置取平均
                NDArray mask = attMask.expandDims(0).expandDims(-1).toType(
                        ai.djl.ndarray.types.DataType.FLOAT32, false); // [1, seq_len, 1]
                NDArray masked = lastHidden.mul(mask);
                NDArray sum = masked.sum(new int[]{1});       // [1, 512]
                NDArray count = mask.sum(new int[]{1});       // [1, 1]
                NDArray mean = sum.div(count);                 // [1, 512]

                // L2 归一化
                NDArray norm = mean.div(mean.norm());
                float[] result = norm.toFloatArray();
                return result;
            }
        } catch (Exception e) {
            log.warn("[RAG] encode 失败: {}", e.getMessage());
            return new float[DIM];
        }
    }
}
