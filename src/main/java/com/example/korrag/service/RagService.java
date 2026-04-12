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

    public Flux<Map<String, Object>> askStream(String userId, String question) {
        processEpochSummarization(userId);
        String historyContext = getHistoryContextForAi(userId);

        // 1. 라우팅 에이전트
        String routingResult = chatClient.prompt()
                .system("당신은 인사 비서팀의 라우팅 팀장입니다. 사용자의 질문 의도를 분석하여 다음 형식으로 답변하세요.\n" +
                        "형식: `INTENT: [분류] QUERY: [검색어]`\n" +
                        "- 분류: [SEARCH], [ACTION], [GENERAL]\n" +
                        "- 검색어: 키워드 위주로 추출, 불필요하면 NONE")
                .user("사용자 질문: " + question + "\n\n최근 맥락:\n" + historyContext)
                .call().content();

        String intent = "GENERAL";
        String optimizedQuery = question;
        if (routingResult != null && routingResult.contains("INTENT:")) {
            if (routingResult.contains("[SEARCH]")) intent = "SEARCH";
            else if (routingResult.contains("[ACTION]")) intent = "ACTION";
            int queryStart = routingResult.indexOf("QUERY:");
            if (queryStart != -1) optimizedQuery = routingResult.substring(queryStart + 6).trim().replace("[", "").replace("]", "");
        }

        // 2. RAG 검색
        List<Map<String, Object>> results = List.of();
        if ("SEARCH".equals(intent) || (!optimizedQuery.equals("NONE") && !optimizedQuery.equals(question))) {
            float[] queryVector = embeddingService.embedQuery(optimizedQuery);
            List<Map<String, Object>> candidates = vectorRepository.searchSimilar(queryVector, retrievalTopK, threshold);
            results = rerankService.rerank(optimizedQuery, candidates, rerankTopK);
        }

        String context = results.isEmpty() ? "(검색 결과 없음)" : results.stream()
                .map(r -> String.format("[%s(%s) %s]: %s", r.get("name"), r.get("accept_no"), r.get("essay_type"), r.get("content")))
                .collect(Collectors.joining("\n\n"));

        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("USER").content(question).build());

        // 3. 멀티 채널 스트리밍 처리기
        final StringBuilder fullAnswer = new StringBuilder();
        final StringBuilder lineBuffer = new StringBuilder();
        
        return chatClient.prompt()
                .user(u -> u.text("당신은 유능한 채용 비서입니다. 모든 대답은 반드시 한국어로 작성하세요. (중국어, 한자 혼용 절대 금지)\n" +
                                "- **메시지 유형 구분 (중요)**:\n" +
                                "  1. [시스템 로그]: 도구 실행 결과 중 `[시스템 지침]`으로 시작하는 내용은 답변의 맨 처음에 그대로 포함하되, 다른 내용과 섞지 마세요.\n" +
                                "  2. [사용자 대화]: 사용자에게 묻는 질문(예: '발송할까요?')이나 일반 답변은 친절한 말투로 작성하세요.\n" +
                                "  3. [승인 토큰]: `[APPROVAL_REQUIRED:...]` 토큰은 반드시 답변의 맨 마지막에 위치시켜야 합니다.\n" +
                                "- **경고**: `[APPROVAL_REQUIRED]` 토큰이 포함된 줄에는 절대로 `[시스템 지침]` 태그를 붙이지 마세요. 승인 요청은 로그가 아니라 사용자에게 직접 묻는 '대화'입니다.\n" +
                                "- 일괄 처리 단계:\n" +
                                "  1. `- 이름 (ID): [선정 근거]` 목록을 먼저 출력.\n" +
                                "  2. 다른 부연 설명 없이 즉시 `sendBulkResultEmail(confirmed=false)` 도구 호출.\n" +
                                "  3. 도구가 반환한 승인 요청 메시지와 토큰을 답변 마지막에 출력.\n\n" +
                                "[참고 지원자 정보]\n" + context + "\n\n" +
                                "[대화 맥락]\n" + historyContext + "\n\n" +
                                "[사용자 질문/지시]\n" + question))
                .stream()
                .chatResponse()
                .map(response -> {
                    String chunk = "";
                    String finishReason = null;
                    
                    if (response.getResult() != null) {
                        if (response.getResult().getOutput() != null) {
                            chunk = response.getResult().getOutput().getText();
                            if (chunk == null) chunk = "";
                        }
                        if (response.getResult().getMetadata() != null) {
                            finishReason = response.getResult().getMetadata().getFinishReason();
                        }
                    }

                    Map<String, Object> event = new java.util.HashMap<>();
                    fullAnswer.append(chunk);
                    lineBuffer.append(chunk);

                    String currentLine = lineBuffer.toString();
                    
                    // 태그 판별 로직
                    boolean isTagLine = currentLine.contains("[시스템 지침]") || 
                                      currentLine.contains("[APPROVAL_REQUIRED]") || 
                                      currentLine.contains("[ACTION_COMPLETED]") || 
                                      currentLine.contains("[ACTION_CANCELLED]");

                    if (currentLine.contains("\n") || (finishReason != null && !finishReason.isEmpty())) {
                        if (isTagLine) {
                            if (currentLine.contains("[시스템 지침]")) event.put("system", currentLine.trim());
                            else if (currentLine.contains("[APPROVAL_REQUIRED]")) event.put("approval", currentLine.trim());
                            else event.put("completed", currentLine.trim());
                            lineBuffer.setLength(0);
                        } else {
                            event.put("text", currentLine);
                            lineBuffer.setLength(0);
                        }
                    } else if (!currentLine.startsWith("[") || currentLine.length() > 50) {
                        // 일반 텍스트는 즉시 흘려보냄
                        event.put("text", currentLine);
                        lineBuffer.setLength(0);
                    }

                    if (finishReason != null && !finishReason.isEmpty()) {
                        chatMessageRepository.save(ChatMessage.builder()
                                .userId(userId).role("ASSISTANT").content(fullAnswer.toString()).build());
                    }
                    return event;
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
