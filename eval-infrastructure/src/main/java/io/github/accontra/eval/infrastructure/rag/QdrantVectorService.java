package io.github.accontra.eval.infrastructure.rag;

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
import java.util.ArrayList;
import java.util.List;

/**
 * A3 Qdrant 向量存储服务。
 *
 * 通过 HTTP REST 调用 Qdrant，替代 Lucene HNSW 的 VectorIndexService。
 * 接口签名与原 VectorIndexService 兼容。
 */
public class QdrantVectorService {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorService.class);

    private final HttpClient http;
    private final String baseUrl;
    private final String collection;
    private final int vectorSize;
    private volatile boolean available = false;

    public QdrantVectorService(QdrantProperties props) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = props.baseUrl();
        this.collection = props.getCollection();
        this.vectorSize = props.getVectorSize();
    }

    /** 初始化：检查连接 + 创建/确认 collection */
    public void init() {
        try {
            health();
            ensureCollection();
            available = true;
            log.info("[Qdrant] 连接成功, collection={}, dimension={}", collection, vectorSize);
        } catch (Exception e) {
            log.warn("[Qdrant] 初始化失败: {} (RAG 将降级到规则检索)", e.getMessage());
            available = false;
        }
    }

    public boolean isAvailable() { return available; }

    /** 健康检查 */
    private void health() throws Exception {
        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/healthz"))
                .timeout(Duration.ofSeconds(3)).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("health check failed: " + resp.statusCode());
    }

    /** 确保 collection 存在 */
    private void ensureCollection() throws Exception {
        // GET 检查是否存在
        var getReq = HttpRequest.newBuilder(URI.create(baseUrl + "/collections/" + collection))
                .timeout(Duration.ofSeconds(5)).GET().build();
        var getResp = http.send(getReq, HttpResponse.BodyHandlers.ofString());
        if (getResp.statusCode() == 200) {
            log.info("[Qdrant] Collection '{}' already exists", collection);
            return;
        }

        // PUT 创建
        String body = String.format(
                "{\"vectors\":{\"size\":%d,\"distance\":\"Cosine\"}}", vectorSize);
        var putReq = HttpRequest.newBuilder(URI.create(baseUrl + "/collections/" + collection))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        var putResp = http.send(putReq, HttpResponse.BodyHandlers.ofString());
        if (putResp.statusCode() != 200) {
            throw new RuntimeException("create collection failed: " + putResp.statusCode() + " " + putResp.body());
        }
        log.info("[Qdrant] Collection '{}' created", collection);
    }

    /** 批量插入/更新 points */
    public void upsert(List<Point> points) {
        if (points.isEmpty()) return;
        try {
            JSONArray pts = new JSONArray();
            for (var p : points) {
                JSONObject pt = new JSONObject();
                pt.set("id", p.id);
                pt.set("vector", p.vector);
                pt.set("payload", p.payload);
                pts.add(pt);
            }
            JSONObject body = new JSONObject();
            body.set("points", pts);

            var req = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/collections/" + collection + "/points?wait=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[Qdrant] Upsert failed: {} {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("[Qdrant] Upsert error: {}", e.getMessage());
        }
    }

    /** KNN 搜索，返回 logId + 相似度 */
    public List<SearchHit> search(float[] queryVector, int k) {
        try {
            JSONObject body = new JSONObject();
            body.set("vector", queryVector);
            body.set("limit", k);
            body.set("with_payload", true);

            var req = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/collections/" + collection + "/points/search"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[Qdrant] Search failed: {} {}", resp.statusCode(), resp.body());
                return List.of();
            }

            JSONObject json = JSONUtil.parseObj(resp.body());
            JSONArray results = json.getJSONArray("result");
            if (results == null || results.isEmpty()) return List.of();

            List<SearchHit> hits = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject r = results.getJSONObject(i);
                long id = r.getLong("id");
                double score = r.getDouble("score");
                JSONObject payload = r.getJSONObject("payload");
                hits.add(new SearchHit(id, score, payload));
            }
            return hits;
        } catch (Exception e) {
            log.warn("[Qdrant] Search error: {}", e.getMessage());
            return List.of();
        }
    }

    // ---- data types ----

    public record Point(long id, float[] vector, JSONObject payload) {}

    public record SearchHit(long logId, double score, JSONObject payload) {
        /** 兼容旧代码：归一化相似度 0~100 */
        public double normalizedScore() {
            return Math.max(0, Math.min(100, score * 100));
        }
    }
}
