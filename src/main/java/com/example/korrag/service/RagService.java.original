package com.example.korrag.service;

import com.example.korrag.entity.ApplicantEssay;
import com.example.korrag.entity.ChatMessage;
import com.example.korrag.repository.VectorStoreRepository;
import com.example.korrag.repository.ApplicantEssayRepository;
import com.example.korrag.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;
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
    private final ChatMessageRepository chatMessageRepository;
    private final OnnxRerankService rerankService;
    private final ChatClient chatClient;

    @Value("${app.rag.retrieval-top-k}") private int retrievalTopK;
    @Value("${app.rag.rerank-top-k}") private int rerankTopK;
    @Value("${app.rag.similarity-threshold}") private double threshold;

    public RagService(ApplicantEssayRepository applicantRepository,
                      OnnxEmbeddingService embeddingService,
                      OnnxRerankService rerankService,
                      VectorStoreRepository vectorRepository,
                      ChatMessageRepository chatMessageRepository,
                      ChatClient.Builder chatClientBuilder,
                      HrNavigationTools navTools,
                      HrActionTools actionTools) {
        this.applicantRepository = applicantRepository;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.vectorRepository = vectorRepository;
        this.chatMessageRepository = chatMessageRepository;
        
        // ChatClient 설정: 기본 시스템 프롬프트 및 툴 바인딩
        this.chatClient = chatClientBuilder
                .defaultSystem("당신은 채용 담당자를 돕는 전문 비서입니다. 제공된 지원자 에세이 정보와 대화 이력을 바탕으로 정확하고 친절하게 답변하세요. " +
                        "필요한 경우 인사 시스템의 도구(화면 이동, 메일 발송 등)를 사용하여 업무를 수행하세요.")
                .defaultTools(navTools, actionTools)
                .build();
        
        log.info("RagService 초기화 완료: Spring AI ChatClient 및 MCP 툴 바인딩 완료");
    }

    /**
     * 특정 유저의 대화 이력이 길어질 경우 핵심 내용을 요약하여 압축합니다. (Summarized Memory)
     */
    private String getSummarizedHistory(String userId, List<ChatMessage> history) {
        if (history.size() < 8) {
            return history.stream()
                    .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                    .collect(Collectors.joining("\n"));
        }

        log.info("유저 {}의 대화 이력 압축 시작 (전체 메시지: {})", userId, history.size());
        
        // 앞부분 요약 대상 추출 (최근 3개 제외)
        String toSummarize = history.stream().limit(history.size() - 3)
                .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                .collect(Collectors.joining("\n"));
                
        String summary = chatClient.prompt()
                .system("당신은 채용 비서의 기억 관리자입니다. 아래의 대화 내용을 핵심 위주로 아주 간결하게 요약하세요. 지원자의 이름이나 핵심 질문 사항은 반드시 포함해야 합니다.")
                .user("요약할 대화 내용:\n" + toSummarize)
                .call()
                .content();
                
        String recent = history.stream().skip(history.size() - 3)
                .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                .collect(Collectors.joining("\n"));

        return "[이전 대화 요약]: " + summary + "\n\n[최근 대화]:\n" + recent;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("RAG 초기 데이터 로딩 및 임베딩 확인 시작...");
        List<ApplicantEssay> applicants = applicantRepository.findAll();
        int skipped = 0;
        int processed = 0;

        for (ApplicantEssay app : applicants) {
            if (checkAndEmbed(app, "essay1", app.getHsgEssay1())) processed++; else skipped++;
            if (checkAndEmbed(app, "essay2", app.getHsgEssay2())) processed++; else skipped++;
            if (checkAndEmbed(app, "essay3", app.getHsgEssay3())) processed++; else skipped++;
            if (checkAndEmbed(app, "essay4", app.getHsgEssay4())) processed++; else skipped++;
        }
        log.info("RAG 초기화 완료! (신규 처리: {}, 건너뜀: {})", processed, skipped);
    }

    private boolean checkAndEmbed(ApplicantEssay app, String type, String content) {
        if (content == null || content.isBlank()) return false;
        if (vectorRepository.existsVector(app.getAcceptNo(), type)) return false;
        embedAndStore(app, type, content);
        return true;
    }

    private void embedAndStore(ApplicantEssay app, String type, String content) {
        if (content == null || content.isBlank()) return;
        float[] vector = embeddingService.embedDocument(content);
        vectorRepository.upsertVector(app.getAcceptNo(), app.getName(), type, 0, content, vector);
    }

    public Map<String, Object> ask(String question) {
        return ask("HR_USER_01", question);
    }

    public Map<String, Object> ask(String userId, String question) {
        // 1. 대화 이력 조회 (최근 10개)
        List<ChatMessage> history = chatMessageRepository.findRecentMessagesAsc(userId, 10);
        String historyContext = history.stream()
                .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                .collect(Collectors.joining("\n"));

        // 2. RAG 검색 (1단계: 벡터 유사도 기반 후보군 추출)
        float[] queryVector = embeddingService.embedQuery(question);
        List<Map<String, Object>> candidates = vectorRepository.searchSimilar(queryVector, retrievalTopK, threshold);

        // 3. RAG 재정렬 (2단계: Reranker를 통한 정밀 평가)
        List<Map<String, Object>> results = rerankService.rerank(question, candidates, rerankTopK);

        String context = results.stream()
                .map(r -> String.format("[%s(%s) %s]: %s", r.get("name"), r.get("accept_no"), r.get("essay_type"), r.get("content")))
                .collect(Collectors.joining("\n\n"));

        // 4. Spring AI ChatClient를 이용한 답변 생성 (툴 호출 포함)
        String answer = chatClient.prompt()
                .user(u -> u.text("아래 정보를 바탕으로 질문에 답하세요.\n\n" +
                                "[참고 지원자 정보]\n{context}\n\n" +
                                "[이전 대화 내역]\n{history}\n\n" +
                                "[사용자 질문]\n{question}")
                        .param("context", context.isEmpty() ? "(검색 결과 없음)" : context)
                        .param("history", historyContext.isEmpty() ? "(이력 없음)" : historyContext)
                        .param("question", question))
                .call()
                .content();

        // 4. 대화 내역 저장
        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("USER").content(question).build());
        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("AI").content(answer).build());

        return Map.of("answer", answer, "sources", results);
    }

    public Flux<ChatResponse> askStream(String userId, String question) {
        // 1. 대화 이력 조회 및 요약 (Summarized Memory)
        List<ChatMessage> history = chatMessageRepository.findRecentMessagesAsc(userId, 15);
        String historyContext = getSummarizedHistory(userId, history);

        // 2. Multi-Agent 1단계: 질문 분석 및 검색어 최적화 (Search Agent)
        String optimizedQuery = chatClient.prompt()
                .system("당신은 인사 전문 검색 요원입니다. 사용자의 질문을 분석하여 RAG 검색에 가장 유리한 검색어(Search Query) 하나를 만드세요. 불필요한 수식어는 빼고 키워드 위주로 반환하세요.")
                .user("사용자 질문: " + question)
                .call().content();
        log.info("[Multi-Agent] 질문 최적화: {} -> {}", question, optimizedQuery);

        // 3. RAG 검색 및 재정렬
        float[] queryVector = embeddingService.embedQuery(optimizedQuery != null ? optimizedQuery : question);
        List<Map<String, Object>> candidates = vectorRepository.searchSimilar(queryVector, retrievalTopK, threshold);
        List<Map<String, Object>> results = rerankService.rerank(question, candidates, rerankTopK);

        String context = results.stream()
                .map(r -> String.format("[%s(%s) %s]: %s", r.get("name"), r.get("accept_no"), r.get("essay_type"), r.get("content")))
                .collect(Collectors.joining("\n\n"));

        // 질문 저장
        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("USER").content(question).build());

        // 4. 최종 답변 생성 (Self-Critique 가이드 포함)
        final StringBuilder fullAnswer = new StringBuilder();
        return chatClient.prompt()
                .user(u -> u.text("아래 정보를 바탕으로 질문에 답하세요. " +
                                "답변 생성 시 스스로 정보를 재검토(Self-Critique)하여, 사실에 근거한 정확한 내용만 출력하세요.\n\n" +
                                "[참고 지원자 정보]\n{context}\n\n" +
                                "[대화 맥락]\n{history}\n\n" +
                                "[사용자 질문]\n{question}")
                        .param("context", context.isEmpty() ? "(검색 결과 없음)" : context)
                        .param("history", historyContext)
                        .param("question", question))
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getText() != null) {
                        fullAnswer.append(response.getResult().getOutput().getText());
                    }
                })
                .doOnComplete(() -> {
                    // 답변 저장
                    chatMessageRepository.save(ChatMessage.builder().userId(userId).role("AI").content(fullAnswer.toString()).build());
                });
    }

    public List<ChatMessage> getChatHistory(String userId) {
        return chatMessageRepository.findRecentMessagesAsc(userId, 20);
    }

    public void clearChatHistory(String userId) {
        log.info("유저 {}의 대화 내역 초기화 시작", userId);
        chatMessageRepository.deleteByUserId(userId);
    }
}
