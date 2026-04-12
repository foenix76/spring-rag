package com.example.korrag.service;

import com.example.korrag.entity.ChatMessage;
import com.example.korrag.repository.ChatMessageRepository;
import com.example.korrag.repository.VectorStoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {

    private final ChatClient chatClient;
    private final OnnxEmbeddingService embeddingService;
    private final VectorStoreRepository vectorRepository;
    private final OnnxRerankService rerankService;
    private final ChatMessageRepository chatMessageRepository;
    private final HrActionTools actionTools;
    private final HrNavigationTools navigationTools;

    @Value("${app.rag.retrieval-top-k:10}")
    private int retrievalTopK;

    @Value("${app.rag.rerank-top-k:3}")
    private int rerankTopK;

    @Value("${app.rag.similarity-threshold:0.7}")
    private float threshold;

    public RagService(ChatClient.Builder chatClientBuilder,
                      OnnxEmbeddingService embeddingService,
                      VectorStoreRepository vectorRepository,
                      OnnxRerankService rerankService,
                      ChatMessageRepository chatMessageRepository,
                      HrActionTools actionTools,
                      HrNavigationTools navigationTools) {
        // 도구(Tool)들을 ChatClient에 기본으로 등록하여 모든 프롬프트에서 사용 가능하게 함
        this.chatClient = chatClientBuilder
                .defaultTools(actionTools, navigationTools)
                .build();
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.rerankService = rerankService;
        this.chatMessageRepository = chatMessageRepository;
        this.actionTools = actionTools;
        this.navigationTools = navigationTools;
    }

    @org.springframework.transaction.annotation.Transactional
    public void processEpochSummarization(String userId) {
        java.util.Optional<ChatMessage> latestSummary = chatMessageRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, "SUMMARY");
        
        long countSinceLastSummary;
        if (latestSummary.isPresent()) {
            countSinceLastSummary = chatMessageRepository.countByUserIdAndIdGreaterThan(userId, latestSummary.get().getId());
        } else {
            countSinceLastSummary = chatMessageRepository.countByUserId(userId);
        }

        if (countSinceLastSummary >= 10) {
            log.info("유저 {}의 계단식 요약 조건 충족 (원문 개수: {})", userId, countSinceLastSummary);
            
            List<ChatMessage> toSummarize;
            String baseSummary = "";

            if (latestSummary.isPresent()) {
                baseSummary = latestSummary.get().getContent();
                toSummarize = chatMessageRepository.findRecentMessagesAsc(userId, (int)countSinceLastSummary)
                        .stream()
                        .filter(m -> m.getId() > latestSummary.get().getId())
                        .limit(5)
                        .toList();
            } else {
                toSummarize = chatMessageRepository.findRecentMessagesAsc(userId, 10)
                        .stream()
                        .limit(5)
                        .toList();
            }

            String contentToCompress = (baseSummary.isEmpty() ? "" : "[기존 요약]: " + baseSummary + "\n") +
                    toSummarize.stream()
                            .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                            .collect(Collectors.joining("\n"));

            String newSummaryContent = chatClient.prompt()
                    .system("당신은 채용 비서의 기억 관리자입니다. 제공된 [기존 요약]과 [추가 대화]를 통합하여 비서가 참고할 '장기 기억 요약본'을 만드세요.\n" +
                            "- 지원자의 이름, 상태, 핵심 질문 사항 등 중요한 정보는 반드시 유지하세요.\n" +
                            "- 매우 간결하고 구조적으로 작성하세요.")
                    .user("통합 요약할 내용:\n" + contentToCompress)
                    .call()
                    .content();

            chatMessageRepository.deleteByUserIdAndRole(userId, "SUMMARY");
            chatMessageRepository.save(ChatMessage.builder()
                    .userId(userId)
                    .role("SUMMARY")
                    .content(newSummaryContent)
                    .build());
            
            log.info("유저 {}의 요약본 갱신 완료", userId);
        }
    }

    private String getHistoryContextForAi(String userId) {
        java.util.Optional<ChatMessage> summary = chatMessageRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, "SUMMARY");
        List<ChatMessage> recentMessages;
        
        if (summary.isPresent()) {
            recentMessages = chatMessageRepository.findRecentMessagesAsc(userId, 20)
                    .stream()
                    .filter(m -> m.getId() > summary.get().getId())
                    .toList();
            
            return String.format("[과거 주요 맥락 요약]\n%s\n\n[최근 대화]\n%s", 
                    summary.get().getContent(),
                    recentMessages.stream()
                            .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                            .collect(Collectors.joining("\n")));
        } else {
            recentMessages = chatMessageRepository.findRecentMessagesAsc(userId, 10);
            return recentMessages.stream()
                    .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                    .collect(Collectors.joining("\n"));
        }
    }

    public Flux<ChatResponse> askStream(String userId, String question) {
        processEpochSummarization(userId);
        String historyContext = getHistoryContextForAi(userId);

        // 1. 라우팅 에이전트: 의도 분석 및 도구 사용 기능 체크
        String routingResult = chatClient.prompt()
                .system("당신은 인사 비서팀의 라우팅 팀장입니다. 사용자의 질문 의도를 분석하여 다음 형식으로 답변하세요.\n" +
                        "형식: `INTENT: [분류] QUERY: [검색어]`\n" +
                        "- 분류: \n" +
                        "  1. [SEARCH]: 지원자의 역량, 에세이 내용, 특정 인물 검색 등이 필요한 경우\n" +
                        "  2. [ACTION]: 이메일 발송, 화면 이동, 시스템 조작 등 도구(Tool) 사용이 필요한 경우\n" +
                        "  3. [GENERAL]: 인사말, 도구 목록 요청, 일반적인 대화\n" +
                        "- 검색어: 사용자 질문에서 검색 대상이나 조건(예: '아르바이트 경험이 있는 지원자')을 추출하세요. 의도가 ACTION이라 하더라도 검색이 필요하다면 반드시 추출하고, 검색이 전혀 불필요한 단순 대화(GENERAL)일 경우에만 'NONE'으로 작성하세요.")
                .user("사용자 질문: " + question + "\n\n최근 맥락:\n" + historyContext)
                .call().content();

        log.info("[Multi-Agent] 라우팅 분석: {}", routingResult);

        String intent = "GENERAL";
        String optimizedQuery = question;
        if (routingResult != null && routingResult.contains("INTENT:")) {
            if (routingResult.contains("[SEARCH]")) intent = "SEARCH";
            else if (routingResult.contains("[ACTION]")) intent = "ACTION";
            
            int queryStart = routingResult.indexOf("QUERY:");
            if (queryStart != -1) {
                optimizedQuery = routingResult.substring(queryStart + 6).trim().replace("[", "").replace("]", "");
            }
        }

        // 2. RAG 검색 및 재정렬 (SEARCH일 때만 또는 QUERY가 존재할 때)
        List<Map<String, Object>> results = List.of();
        if ("SEARCH".equals(intent) || (!optimizedQuery.equals("NONE") && !optimizedQuery.equals(question))) {
            float[] queryVector = embeddingService.embedQuery(optimizedQuery);
            List<Map<String, Object>> candidates = vectorRepository.searchSimilar(queryVector, retrievalTopK, threshold);
            results = rerankService.rerank(optimizedQuery, candidates, rerankTopK);
        }

        String context = results.isEmpty() ? "(해당 조건에 부합하는 지원자를 찾을 수 없습니다. 추가 정보를 요청하거나 조건이 구체적인지 확인하세요)" : results.stream()
                .map(r -> String.format("[%s(%s) %s]: %s", r.get("name"), r.get("accept_no"), r.get("essay_type"), r.get("content")))
                .collect(Collectors.joining("\n\n"));

        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("USER").content(question).build());

        // 3. 최종 답변 생성 (도구 호출 활성화)
        final StringBuilder fullAnswer = new StringBuilder();
        return chatClient.prompt()
                .user(u -> u.text("당신은 유능한 채용 비서입니다. 모든 대답은 반드시 한국어로 작성하세요. (중국어, 영어 등 타 언어 혼용 절대 금지) 아래 정보를 바탕으로 인사 업무를 수행하세요.\n" +
                                "- 도구(Tool) 사용이 필요한 경우 망설이지 말고 호출하세요.\n" +
                                "- '사용 가능 도구'에 대해 물으면 등록된 도구(sendResultEmail, navigateTo 등)의 기능과 사용법을 친절히 안내하세요. (단, 안내 시 UI 오작동을 막기 위해 `[APPROVAL_REQUIRED:...]` 같은 실제 토큰 문자열은 절대 화면에 출력하지 마세요.)\n" +
                                "- sendResultEmail 툴 호출 시 resultType 파라미터는 반드시 대문자 'PASS' 또는 'FAIL'로만 전달하세요.\n" +
                                "- 여러 명에게 이메일 발송 등 일괄 처리가 필요한 경우, 다음 순서를 엄격히 지키세요:\n" +
                                "  1) 먼저 마크다운 글머리 기호(`- 이름 (ID)`)를 사용하여 대상자 목록만 화면에 출력합니다.\n" +
                                "  2) 대상자 목록 출력 후 다른 부연 설명을 덧붙이지 말고 **즉시 `sendBulkResultEmail` 툴을 `confirmed=false`로 호출**합니다.\n" +
                                "  3) 툴 호출 결과로 반환된 `[APPROVAL_REQUIRED...]` 문자열을 원본 그대로 출력하고 **응답을 마칩니다.**\n" +
                                "- **경고**: 절대로 사용자를 대신하여 승인 멘트를 지어내거나 출력하지 마세요.\n" +
                                "- 만약 사용자가 승인을 취소(`[ACTION_CANCELLED...` 토큰 포함)했다면, 어떠한 멘트나 부연 설명도 하지 말고 **아무런 내용이 없는 빈 응답(침묵)**만 출력하세요.\n\n" +
                                "[참고 지원자 정보]\n{context}\n\n" +
                                "[대화 맥락]\n{history}\n\n" +
                                "[사용자 질문/지시]\n{question}")
                        .param("context", context)
                        .param("history", historyContext)
                        .param("question", question))
                .stream()
                .chatResponse()
                .map(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        String content = response.getResult().getOutput().getText();
                        fullAnswer.append(content != null ? content : "");
                    }
                    return response;
                })
                .doOnComplete(() -> {
                    chatMessageRepository.save(ChatMessage.builder()
                            .userId(userId)
                            .role("ASSISTANT")
                            .content(fullAnswer.toString())
                            .build());
                });
    }

    public List<ChatMessage> getChatHistory(String userId) {
        return chatMessageRepository.findRecentMessagesAsc(userId, 50);
    }

    @org.springframework.transaction.annotation.Transactional
    public void clearChatHistory(String userId) {
        chatMessageRepository.deleteByUserId(userId);
    }
}
