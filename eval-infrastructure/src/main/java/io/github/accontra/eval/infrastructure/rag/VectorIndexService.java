package io.github.accontra.eval.infrastructure.rag;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.codecs.lucene99.Lucene99Codec;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A3 RAG: Lucene HNSW 向量索引服务。
 *
 * 使用 Lucene 9.x KnnVectorField 实现 HNSW 近似最近邻搜索。
 * 索引文件存储在 data/rag-vector-index/。
 */
@Component
public class VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);
    private static final int DIM = 512;
    private static final Path INDEX_DIR = Path.of("data/rag-vector-index");

    private FSDirectory directory;
    private IndexWriter writer;
    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(INDEX_DIR);
            directory = FSDirectory.open(INDEX_DIR);
            var config = new IndexWriterConfig()
                    .setCodec(new Lucene99Codec())
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(directory, config);
            this.available = true;
            log.info("[RAG] Lucene HNSW 索引初始化完成, path={}, docs={}",
                    INDEX_DIR.toAbsolutePath(), writer.getDocStats().numDocs);
        } catch (Exception e) {
            log.error("[RAG] 索引初始化失败", e);
            available = false;
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (writer != null) { writer.commit(); writer.close(); }
            if (directory != null) directory.close();
        } catch (IOException e) {
            log.warn("[RAG] 索引关闭异常: {}", e.getMessage());
        }
    }

    public boolean isAvailable() { return available; }

    /**
     * 添加一条案例到索引。
     */
    public void addDocument(long logId, float[] embedding,
                            String indexCode, String indexName,
                            String dataValue, String llmReason) {
        if (!available || writer == null) return;
        var doc = new Document();
        doc.add(new StoredField("logId", logId));
        doc.add(new StringField("indexCode", indexCode != null ? indexCode : "", Field.Store.YES));
        doc.add(new TextField("indexName", indexName != null ? indexName : "", Field.Store.YES));
        doc.add(new StoredField("dataValue", dataValue != null ? dataValue : ""));
        doc.add(new StoredField("llmReason", llmReason != null ? llmReason : ""));
        doc.add(new KnnVectorField("embedding", embedding,
                VectorSimilarityFunction.DOT_PRODUCT));

        try {
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            log.error("[RAG] 索引写入失败, logId={}", logId, e);
        }
    }

    /**
     * 搜索 K 个最近邻。
     *
     * @return 按相似度降序排列的结果列表
     */
    public List<SearchResult> search(float[] queryVector, int k) {
        if (!available) return List.of();
        try {
            var reader = DirectoryReader.open(directory);
            var searcher = new IndexSearcher(reader);

            var query = new KnnVectorQuery("embedding", queryVector, k);
            var hits = searcher.search(query, k);

            List<SearchResult> results = new ArrayList<>();
            for (var hit : hits.scoreDocs) {
                var doc = searcher.storedFields().document(hit.doc);
                results.add(new SearchResult(
                        Long.parseLong(doc.get("logId")),
                        doc.get("indexCode"),
                        doc.get("indexName"),
                        doc.get("dataValue"),
                        doc.get("llmReason"),
                        hit.score
                ));
            }

            reader.close();
            results.sort((a, b) -> Float.compare(b.score, a.score));
            return results;

        } catch (IOException e) {
            log.error("[RAG] 向量检索失败", e);
            return List.of();
        }
    }

    /** 索引中的文档数量 */
    public int docCount() {
        if (writer == null) return 0;
        return writer.getDocStats().numDocs;
    }

    public record SearchResult(long logId, String indexCode, String indexName,
                               String dataValue, String llmReason, float score) {}
}
