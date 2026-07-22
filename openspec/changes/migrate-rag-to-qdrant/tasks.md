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
