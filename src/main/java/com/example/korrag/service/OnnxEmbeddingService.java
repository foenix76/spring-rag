package com.example.korrag.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OnnxEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingService.class);

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int maxTokenLength;

    public OnnxEmbeddingService(
            OrtEnvironment env,
            OrtSession session,
            HuggingFaceTokenizer tokenizer,
            @Value("${app.embedding.max-token-length}") int maxTokenLength) {
        this.env = env;
        this.session = session;
        this.tokenizer = tokenizer;
        this.maxTokenLength = maxTokenLength;
    }

    public float[] embedDocument(String text) {
        return embed("passage: " + text);
    }

    public float[] embedQuery(String query) {
        return embed("query: " + query);
    }

    private float[] embed(String text) {
        try {
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();

            int seqLen = Math.min(inputIds.length, maxTokenLength);
            if (inputIds.length > maxTokenLength) {
                inputIds = java.util.Arrays.copyOf(inputIds, maxTokenLength);
                attentionMask = java.util.Arrays.copyOf(attentionMask, maxTokenLength);
            }

            long[][] inputIdsBatch = {inputIds};
            long[][] attentionMaskBatch = {attentionMask};

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", OnnxTensor.createTensor(env, inputIdsBatch));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, attentionMaskBatch));

            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();
                int hiddenDim = lastHiddenState[0][0].length;
                float[] pooled = new float[hiddenDim];
                float maskSum = 0;

                for (int i = 0; i < seqLen; i++) {
                    float mask = attentionMask[i];
                    maskSum += mask;
                    for (int j = 0; j < hiddenDim; j++) {
                        pooled[j] += lastHiddenState[0][i][j] * mask;
                    }
                }
                for (int j = 0; j < hiddenDim; j++) {
                    pooled[j] /= maskSum;
                }

                // L2 Normalization
                float norm = 0;
                for (float v : pooled) norm += v * v;
                norm = (float) Math.sqrt(norm);
                for (int j = 0; j < hiddenDim; j++) pooled[j] /= norm;

                return pooled;
            } finally {
                for (OnnxTensor tensor : inputs.values()) tensor.close();
            }
        } catch (OrtException e) {
            log.error("ONNX 임베딩 실패: {}", e.getMessage(), e);
            throw new RuntimeException("임베딩 처리 중 오류 발생", e);
        }
    }
}
