---
comet_change: migrate-rag-to-qdrant
role: technical-design
canonical_spec: openspec
---

# Lucene HNSW → Qdrant 迁移 — 技术设计

## 1. 架构变更

```
之前:  VectorRagService → VectorIndexService (Lucene HNSW)
之后:  VectorRagService → QdrantVectorService (HTTP → Qdrant)
                          EmbeddingService (不变)
```

Qdrant 作为独立进程运行（Docker 或 exe），Java 通过 HTTP REST 调用。

## 2. QdrantVectorService

手写 `java.net.http.HttpClient`，3 个核心操作：

```java
// 创建 collection
POST /collections/eval_cases { "vectors": {"size":512, "distance":"Cosine"} }

// 批量 upsert
PUT /collections/eval_cases/points
{ "points": [{ "id":168, "vector":[...], "payload":{...} }] }

// 搜索
POST /collections/eval_cases/points/search
{ "vector":[...], "limit":3, "with_payload":true }
```

接口保持与 `VectorIndexService` 相同签名，`VectorRagService` 只需改注入类型。

## 3. 移除 Lucene

- `lucene-core:9.12.0` → 删除
- `lucene-analysis-common:9.12.0` → 删除
- `VectorIndexService.java` → 删除

## 4. 配置

```yaml
qdrant:
  host: localhost
  port: 6333
  collection: eval_cases
```

## 5. 部署

`restart.sh` 添加：
```bash
# Qdrant
docker start qdrant 2>/dev/null || docker run -d --name qdrant -p 6333:6333 qdrant/qdrant
```
