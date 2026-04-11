package com.example.korrag.config;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class OnnxRerankConfig {

    private static final Logger log = LoggerFactory.getLogger(OnnxRerankConfig.class);

    @Value("${app.reranker.model-path}")
    private String modelPath;

    @Value("${app.reranker.tokenizer-path}")
    private String tokenizerPath;

    @Bean(name = "rerankSession")
    public OrtSession rerankSession(OrtEnvironment env) throws OrtException, IOException {
        Path path = java.nio.file.Paths.get(modelPath);
        if (!java.nio.file.Files.exists(path)) {
            log.warn("Reranker 모델 파일을 찾을 수 없습니다: {}. Rerank 기능이 비활성화될 수 있습니다.", path.toAbsolutePath());
            return null;
        }
        byte[] modelBytes = java.nio.file.Files.readAllBytes(path);
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        log.info("Reranker 모델 로드: {}", path.toAbsolutePath());
        return env.createSession(modelBytes, opts);
    }

    @Bean(name = "rerankTokenizer")
    public HuggingFaceTokenizer rerankTokenizer() throws IOException {
        Path path = java.nio.file.Paths.get(tokenizerPath);
        if (!java.nio.file.Files.exists(path)) {
            log.warn("Reranker 토크나이저 파일을 찾을 수 없습니다: {}.", path.toAbsolutePath());
            return null;
        }
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(path);
        log.info("Reranker 토크나이저 로드: {}", path.toAbsolutePath());
        return tokenizer;
    }
}
