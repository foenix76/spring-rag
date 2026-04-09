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
        ClassPathResource modelResource = new ClassPathResource(modelPath);
        byte[] modelBytes = modelResource.getInputStream().readAllBytes();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        log.info("ONNX 모델 로드 준비: {}", modelPath);
        return env.createSession(modelBytes, opts);
    }

    @Bean
    public HuggingFaceTokenizer huggingFaceTokenizer() throws IOException {
        ClassPathResource tokenizerResource = new ClassPathResource(tokenizerPath);
        Path tempDir = java.nio.file.Files.createTempDirectory("hf-tokenizer");
        Path tempFile = tempDir.resolve("tokenizer.json");
        java.nio.file.Files.copy(tokenizerResource.getInputStream(), tempFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(tempFile);
        log.info("HuggingFace 토크나이저 로드 완료: {}", tokenizerPath);
        return tokenizer;
    }
}
