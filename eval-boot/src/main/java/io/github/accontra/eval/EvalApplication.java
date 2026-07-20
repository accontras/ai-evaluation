package io.github.accontra.eval;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("io.github.accontra.eval.infrastructure.mapper")
public class EvalApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvalApplication.class, args);
    }
}
