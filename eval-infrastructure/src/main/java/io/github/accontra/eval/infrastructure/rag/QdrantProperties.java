package io.github.accontra.eval.infrastructure.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    private String host = "localhost";
    private int port = 6333;
    private String collection = "eval_cases";
    private int vectorSize = 512;

    public String getHost() { return host; }
    public void setHost(String v) { host = v; }
    public int getPort() { return port; }
    public void setPort(int v) { port = v; }
    public String getCollection() { return collection; }
    public void setCollection(String v) { collection = v; }
    public int getVectorSize() { return vectorSize; }
    public void setVectorSize(int v) { vectorSize = v; }

    public String baseUrl() { return "http://" + host + ":" + port; }
}
