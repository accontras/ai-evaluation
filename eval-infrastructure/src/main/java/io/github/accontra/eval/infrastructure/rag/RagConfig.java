package io.github.accontra.eval.infrastructure.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    @ConfigurationProperties(prefix = "qdrant")
    public QdrantProperties qdrantProperties() {
        return new QdrantProperties();
    }

    @Bean
    public QdrantVectorService qdrantVectorService(QdrantProperties props) {
        QdrantVectorService svc = new QdrantVectorService(props);
        svc.init();
        return svc;
    }
}
