## Context

当前 `VectorIndexService` 基于 Lucene 9.x KnnVectorField + HNSW 索引，168 条数据。`QdrantVectorService` 替换为 HTTP REST 调用。

## Goals / Non-Goals

**Goals**: 替换向量存储为 Qdrant，保持 EmbeddingService 不变。
**Non-Goals**: 不改 ONNX 模型、不改检索策略、不做分布式。

## Decisions

### D1: Qdrant REST API 客户端 — 手写 HTTP 调用

不用 qdrant-java-client SDK。直接 `java.net.http.HttpClient` POST JSON。

**理由**: 你只有 3 个操作（create collection / upsert / search），300 行代码搞定，不加依赖。

### D2: Collection 设计

```json
PUT /collections/eval_cases
{
  "vectors": { "size": 512, "distance": "Cosine" }
}

// Point 结构
{
  "id": 168,           // = eval_indicator_log.id
  "vector": [0.023, ...],  // 512 维
  "payload": {
    "indexCode": "COST_DEV",
    "indexName": "成本偏差",
    "dataValue": "17",
    "llmScore": 30.0,
    "llmReason": "...",
    "diffLevel": "NOTABLE"
  }
}
```

Payload 带业务字段，检索结果直接包含元数据，不再需要额外查 DB。

### D3: 接口兼容

`QdrantVectorService` 保持与 `VectorIndexService` 相同的方法签名：
- `isAvailable()` → HTTP health check
- `add(long id, float[] vector)` → PUT /points
- `search(float[] query, int k)` → POST /points/search

`VectorRagService` 只需改注入类型，不改逻辑。

### D4: Qdrant 部署

Windows 下用 Docker：`docker run -d -p 6333:6333 qdrant/qdrant`
restart.sh 加 `docker start qdrant` 检查逻辑。
无 Docker 则用户手动下载 qdrant.exe。

## Risks

- **Qdrant 不可用**：`isAvailable()` 返回 false → RAG 降级到 SimilarCaseService 规则检索
- **首次迁移**：168 条全量 upsert，~2-3 秒完成
