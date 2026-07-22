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
