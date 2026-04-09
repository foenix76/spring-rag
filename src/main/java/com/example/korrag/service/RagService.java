package com.example.korrag.service;

import com.example.korrag.entity.ApplicantEssay;
import com.example.korrag.repository.VectorStoreRepository;
import com.example.korrag.repository.ApplicantEssayRepository;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ApplicantEssayRepository applicantRepository;
    private final OnnxEmbeddingService embeddingService;
    private final VectorStoreRepository vectorRepository;
    private final OpenAiChatModel chatModel;

    @Value("${app.rag.top-k}") private int topK;
    @Value("${app.rag.similarity-threshold}") private double threshold;

    public RagService(ApplicantEssayRepository applicantRepository,
                      OnnxEmbeddingService embeddingService,
                      VectorStoreRepository vectorRepository,
                      @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.applicantRepository = applicantRepository;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.chatModel = OpenAiChatModel.withApiKey(apiKey);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("RAG 초기 데이터 로딩 및 임베딩 시작...");
        List<ApplicantEssay> applicants = applicantRepository.findAll();
        for (ApplicantEssay app : applicants) {
            embedAndStore(app, "essay1", app.getHsgEssay1());
            embedAndStore(app, "essay2", app.getHsgEssay2());
            embedAndStore(app, "essay3", app.getHsgEssay3());
            embedAndStore(app, "essay4", app.getHsgEssay4());
        }
        log.info("임베딩 완료!");
    }

    private void embedAndStore(ApplicantEssay app, String type, String content) {
        if (content == null || content.isBlank()) return;
        float[] vector = embeddingService.embedDocument(content);
        vectorRepository.upsertVector(app.getAcceptNo(), app.getName(), type, 0, content, vector);
    }

    public Map<String, Object> ask(String question) {
        float[] queryVector = embeddingService.embedQuery(question);
        List<Map<String, Object>> results = vectorRepository.searchSimilar(queryVector, topK, threshold);

        String context = results.stream()
                .map(r -> String.format("[%s(%s) %s]: %s", r.get("name"), r.get("accept_no"), r.get("essay_type"), r.get("content")))
                .collect(Collectors.joining("\n\n"));

        String prompt = "다음 지원자 에세이 내용을 바탕으로 질문에 답하세요. 관련 지원자가 있다면 반드시 이름을 언급하세요.\n\n" +
                        "[컨텍스트]\n" + context + "\n\n[질문]\n" + question;

        String answer = chatModel.generate(prompt);
        return Map.of("answer", answer, "sources", results);
    }
}
