# Comet Design Handoff

- Change: migrate-rag-to-qdrant
- Phase: design
- Mode: compact
- Context hash: d8c3fbe689e136d8798818ec6a88ff56e4108ca657feef746dab6bb7c1f89d44

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/migrate-rag-to-qdrant/proposal.md

- Source: openspec/changes/migrate-rag-to-qdrant/proposal.md
- Lines: 1-26
- SHA256: 4cc94e7dcf1f573a98197b05f50beaf5538148d1b86c35f4ed79714ebd95575c

```md
## Why

当前 Lucene HNSW 作为向量存储存在三个限制：(1) 索引管理手动——增删需要自己维护图结构；(2) 无法按 payload 字段过滤，检索后需后处理；(3) 纯 Java 生态，未来扩展受限。换 Qdrant（Rust 实现，REST API）获得自动索引管理、payload filtering、跨语言兼容。

## What Changes

- **新增 QdrantVectorService**：替代 VectorIndexService，HTTP 调 Qdrant REST API
- **移除 Lucene 依赖**：lucene-core、lucene-analysis-common 从 pom.xml 删除
- **修改 VectorRagService**：依赖从 VectorIndexService 切到 QdrantVectorService
- **修改 RagIndexInitializer**：迁移逻辑从 Lucene 换 Qdrant collection
- **restart.sh 添加 qdrant 进程**：启动/停止管理
- **application.yml 添加 qdrant 配置**：host/port/collection

## Capabilities

### New Capabilities
- `qdrant-vector-store`: Qdrant 向量存储，HTTP API 调用的向量增删检索，collection 管理

### Modified Capabilities
<!-- 无已有 capability 被修改 -->

## Impact

- **新增**：QdrantVectorService、qdrant 配置
- **修改**：VectorRagService、RagIndexInitializer、pom.xml、restart.sh
- **移除**：VectorIndexService、Lucene 依赖
```

## openspec/changes/migrate-rag-to-qdrant/design.md

- Source: openspec/changes/migrate-rag-to-qdrant/design.md
- Lines: 1-61
- SHA256: f67f612bef542c7fafa70c47be911221eaa54ed621d14ebb3b372de4cebb1f00

```md
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
```

## openspec/changes/migrate-rag-to-qdrant/tasks.md

- Source: openspec/changes/migrate-rag-to-qdrant/tasks.md
- Lines: 1-25
- SHA256: 949ae48d0db02cbd7a366a858a05028598557213e1ef7c622c3bae1a7365043d

```md
## 1. Qdrant 配置

- [ ] 1.1 application.yml 添加 qdrant: host/port/collection
- [ ] 1.2 QdrantProperties 配置类

## 2. QdrantVectorService

- [ ] 2.1 创建 QdrantVectorService: init/createCollection/upsert/search/health
- [ ] 2.2 java.net.http.HttpClient 手写 REST 调用

## 3. 替换 VectorIndexService

- [ ] 3.1 VectorRagService 依赖从 VectorIndexService 切到 QdrantVectorService
- [ ] 3.2 RagIndexInitializer 适配 Qdrant upsert 替代 Lucene add
- [ ] 3.3 移除 lucene-core/lucene-analysis-common 依赖

## 4. 部署 + 文档

- [ ] 4.1 restart.sh 添加 qdrant 进程检查/启动
- [ ] 4.2 README 更新 Qdrant 部署说明

## 5. 验证

- [ ] 5.1 编译通过 + 启动验证 Qdrant 连接
- [ ] 5.2 A3.3 跑批对比 Lucene 版结果
```

