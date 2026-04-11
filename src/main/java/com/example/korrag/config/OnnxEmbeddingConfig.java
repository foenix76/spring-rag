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
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class OnnxEmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingConfig.class);

    @Value("${app.embedding.model-path}")
    private String modelPath;

    @Value("${app.embedding.tokenizer-path}")
    private String tokenizerPath;

    @Bean
    public OrtEnvironment ortEnvironment() {
        return OrtEnvironment.getEnvironment();
    }

    @Bean
    public OrtSession ortSession(OrtEnvironment env) throws OrtException, IOException {
        Path path = java.nio.file.Paths.get(modelPath);
        if (!java.nio.file.Files.exists(path)) {
            throw new IOException("ONNX 모델 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }
        byte[] modelBytes = java.nio.file.Files.readAllBytes(path);
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        log.info("외부 파일 시스템에서 ONNX 모델 로드: {}", path.toAbsolutePath());
        return env.createSession(modelBytes, opts);
    }

    @Bean
    public HuggingFaceTokenizer huggingFaceTokenizer() throws IOException {
        Path path = java.nio.file.Paths.get(tokenizerPath);
        if (!java.nio.file.Files.exists(path)) {
            throw new IOException("토크나이저 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }
        // DJL HuggingFaceTokenizer는 파일 경로를 직접 받음
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(path);
        log.info("외부 파일 시스템에서 HuggingFace 토크나이저 로드: {}", path.toAbsolutePath());
        return tokenizer;
    }
}
