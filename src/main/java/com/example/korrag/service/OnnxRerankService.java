package com.example.korrag.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OnnxRerankService {

    private static final Logger log = LoggerFactory.getLogger(OnnxRerankService.class);

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int maxTokenLength;

    public OnnxRerankService(
            OrtEnvironment env,
            @Qualifier("rerankSession") OrtSession session,
            @Qualifier("rerankTokenizer") HuggingFaceTokenizer tokenizer,
            @Value("${app.reranker.max-token-length}") int maxTokenLength) {
        this.env = env;
        this.session = session;
        this.tokenizer = tokenizer;
        this.maxTokenLength = maxTokenLength;
    }

    /**
     * 질문과 문서 리스트를 받아 재정렬된 결과를 반환합니다.
     */
    public List<Map<String, Object>> rerank(String query, List<Map<String, Object>> documents, int topK) {
        if (session == null || tokenizer == null || documents.isEmpty()) {
            return documents.stream().limit(topK).collect(Collectors.toList());
        }

        List<RerankScore> scores = new ArrayList<>();

        for (Map<String, Object> doc : documents) {
            String passage = (String) doc.get("content");
            try {
                double score = computeScore(query, passage);
                doc.put("rerank_score", score);
                scores.add(new RerankScore(doc, score));
            } catch (Exception e) {
                log.error("Rerank score 계산 실패: {}", e.getMessage());
                doc.put("rerank_score", 0.0);
                scores.add(new RerankScore(doc, 0.0));
            }
        }

        // 점수 내림차순 정렬
        return scores.stream()
                .sorted(Comparator.comparingDouble(RerankScore::score).reversed())
                .limit(topK)
                .map(RerankScore::doc)
                .collect(Collectors.toList());
    }

    private double computeScore(String query, String passage) throws OrtException {
        // [CLS] query [SEP] passage [SEP] 형태의 Cross-Encoding
        Encoding encoding = tokenizer.encode(query, passage);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] typeIds = encoding.getTypeIds();

        int seqLen = Math.min(inputIds.length, maxTokenLength);
        long[] truncatedInputIds = java.util.Arrays.copyOf(inputIds, seqLen);
        long[] truncatedAttentionMask = java.util.Arrays.copyOf(attentionMask, seqLen);
        long[] truncatedTypeIds = java.util.Arrays.copyOf(typeIds, seqLen);

        long[][] inputIdsBatch = {truncatedInputIds};
        long[][] attentionMaskBatch = {truncatedAttentionMask};

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, inputIdsBatch));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, attentionMaskBatch));

        try (OrtSession.Result result = session.run(inputs)) {
            // BGE-Reranker-v2-m3의 출력은 보통 [batch_size, 1] 형태의 logit
            float[][] output = (float[][]) result.get(0).getValue();
            float logit = output[0][0];
            
            // Sigmoid 적용하여 0~1 사이 점수로 변환 (선택 사항, 여기서는 로짓 그대로 반환 후 정렬)
            return sigmoid(logit);
        } finally {
            for (OnnxTensor tensor : inputs.values()) tensor.close();
        }
    }

    private double sigmoid(float x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private record RerankScore(Map<String, Object> doc, double score) {}
}
