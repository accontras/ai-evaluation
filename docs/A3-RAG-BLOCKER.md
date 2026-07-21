# A3 RAG 落地阻塞点

> 2026-07-21 | 状态: ✅ 已解除（依赖+模型到位），待编译验证

---

## 现状

A3.1 的 Java 代码全部完成：

| 文件 | 状态 |
|------|------|
| `EmbeddingService` (rag/) | ✅ 文本→512维向量 |
| `VectorIndexService` (rag/) | ✅ Lucene HNSW 索引 + KNN |
| `VectorRagService` (rag/) | ✅ 向量优先/规则降级 |
| `RagIndexInitializer` (rag/) | ✅ 历史数据批量迁移 |
| `LlmScoringStrategy.buildFewShot()` | ✅ 向量→规则三级降级 |
| `SummarizeResultHandler` (H6) | ✅ 增量索引更新钩子 |
| `CacheConfig` / `MultiModelCompareService` / `EvaluationDomainService` | ✅ 依赖注入适配 |
| `EndToEndTest` | ✅ 测试适配 |

**设计文档**: `wiki/ai/RAG-落地实践指南.md` + `wiki/ai/RAG-基础知识-从零到懂.md`

---

## 阻塞点

### 1. Maven 依赖无法下载

需要的依赖（已在 `eval-infrastructure/pom.xml` 中注释掉）：

```xml
ai.djl:djl-core:0.31.0
ai.djl.onnxruntime:onnxruntime-engine:0.31.0
ai.djl.huggingface:tokenizers:0.31.0
org.apache.lucene:lucene-core:9.12.0
org.apache.lucene:lucene-analysis-common:9.12.0
```

**原因**: `D:\maven\conf\settings.xml` 中配置了阿里云镜像 `<mirrorOf>*</mirrorOf>`，所有仓库请求被劫持。DJL 和 Lucene 在 Maven Central 上，阿里云镜像未同步。

### 2. ONNX 模型文件无法下载

需要的文件（放到 `eval-infrastructure/src/main/resources/rag/`）：

- `model.onnx` (~23MB) — bge-small-zh ONNX 模型
- `tokenizer.json` (~1MB) — 分词器配置

**原因**: HuggingFace (`huggingface.co`) 网络不可达。尝试了镜像 `hf-mirror.com`，但 Xenova/bge-small-zh 版本不存在。

---

## 解除方案

### 方案 A：修复 Maven（推荐，改一行配置）

编辑 `D:\maven\conf\settings.xml`，找到阿里云 mirror 块（约第 160 行）：

```xml
<!-- 改前 -->
<mirrorOf>*</mirrorOf>

<!-- 改后 -->
<mirrorOf>*,!central</mirrorOf>
```

效果：Maven Central 直连，其他依赖继续走阿里云。改完后：

```bash
# 1. 取消 pom.xml 中 DJL + Lucene 依赖的注释
# 2. 编译
cd eval-system && mvn compile -U
```

### 方案 B：手动安装 jar

如果 Maven Central 同样不可达（企业网络限制），从可上网的机器下载 jar 后手动安装：

```bash
mvn install:install-file -Dfile=djl-core-0.31.0.jar -DgroupId=ai.djl -DartifactId=djl-core -Dversion=0.31.0 -Dpackaging=jar
# ... 同样处理其余 4 个 jar
```

### ONNX 模型文件

从可访问 HuggingFace 的机器下载：

```
https://huggingface.co/Xenova/bge-small-zh/resolve/main/onnx/model.onnx
https://huggingface.co/Xenova/bge-small-zh/resolve/main/tokenizer.json
```

放到 `eval-infrastructure/src/main/resources/rag/` 目录。

**备选**：如果 HuggingFace 完全不可达，也可以用 Python 从国内源导出：

```bash
pip install optimum onnx onnxruntime
optimum-cli export onnx --model BAAI/bge-small-zh ./bge-small-zh-onnx/
# 复制 model.onnx 和 tokenizer.json 到 resources/rag/
```

---

## 代码降级策略（已实现）

即使依赖和模型都不在，系统依然正常运行——`EmbeddingService.init()` 检测到模型文件缺失会设 `available=false`，所有 RAG 检索自动降级到原有的 `SimilarCaseService`（规则相似度检索）。不影响任何现有功能。
