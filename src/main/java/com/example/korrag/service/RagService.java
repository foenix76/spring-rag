package com.example.korrag.service;

import com.example.korrag.entity.ChatMessage;
import com.example.korrag.repository.ChatMessageRepository;
import com.example.korrag.repository.VectorStoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
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

    @Value("${app.rag.retrieval-top-k:10}") private int retrievalTopK;
    @Value("${app.rag.rerank-top-k:3}") private int rerankTopK;
    @Value("${app.rag.similarity-threshold:0.7}") private float threshold;

    public RagService(ChatClient.Builder chatClientBuilder,
                      OnnxEmbeddingService embeddingService,
                      VectorStoreRepository vectorRepository,
                      OnnxRerankService rerankService,
                      ChatMessageRepository chatMessageRepository,
                      HrActionTools actionTools,
                      HrNavigationTools navigationTools) {
        // 기본 툴은 비워두고, askStream 호출 시 세션별로 래핑하여 주입함
        this.chatClient = chatClientBuilder.build();
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
        long count = latestSummary.isPresent() ? chatMessageRepository.countByUserIdAndIdGreaterThan(userId, latestSummary.get().getId()) : chatMessageRepository.countByUserId(userId);
        if (count >= 10) {
            log.info("유저 {} 요약 시작", userId);
            List<ChatMessage> history = chatMessageRepository.findRecentMessagesAsc(userId, 10);
            String summary = chatClient.prompt().system("채용 비서 기억 관리자. 간결한 요약본 작성.").user(history.toString()).call().content();
            chatMessageRepository.deleteByUserIdAndRole(userId, "SUMMARY");
            chatMessageRepository.save(ChatMessage.builder().userId(userId).role("SUMMARY").content(summary).build());
        }
    }

    private String getHistoryContextForAi(String userId) {
        java.util.Optional<ChatMessage> summary = chatMessageRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, "SUMMARY");
        List<ChatMessage> history = chatMessageRepository.findRecentMessagesAsc(userId, 20);
        return summary.map(chatMessage -> "[과거 요약]\n" + chatMessage.getContent() + "\n\n[최근 대화]\n").orElse("") + 
               history.stream().map(m -> m.getRole() + ": " + m.getContent()).collect(Collectors.joining("\n"));
    }

    public Flux<Map<String, Object>> askStream(String userId, String question) {
        processEpochSummarization(userId);
        String history = getHistoryContextForAi(userId);

        // 1. 라우팅
        String routing = chatClient.prompt().system("라우팅 팀장. INTENT: [SEARCH|ACTION|GENERAL] QUERY: [검색어]").user(question).call().content();
        String intent = routing.contains("[SEARCH]") ? "SEARCH" : (routing.contains("[ACTION]") ? "ACTION" : "GENERAL");
        
        List<Map<String, Object>> searchResults = List.of();
        if ("SEARCH".equals(intent) || (routing.contains("QUERY:") && !routing.contains("QUERY: NONE"))) {
            float[] vec = embeddingService.embedQuery(question);
            searchResults = rerankService.rerank(question, vectorRepository.searchSimilar(vec, retrievalTopK, threshold), rerankTopK);
        }
        String context = searchResults.isEmpty() ? "(정보 없음)" : searchResults.stream().map(r -> String.format("[%s(%s)]: %s", r.get("name"), r.get("accept_no"), r.get("content"))).collect(Collectors.joining("\n"));

        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("USER").content(question).build());

        // 2. [설계 핵심] 사이드 채널 싱크 (툴 결과 즉시 전송용)
        Sinks.Many<Map<String, Object>> sideChannel = Sinks.many().multicast().directBestEffort();
        final StringBuilder fullAnswer = new StringBuilder();

        // 3. 툴 실행 가로채기 (Proxy ToolCallbacks)
        List<ToolCallback> wrappedTools = new ArrayList<>();
        try {
            // HrNavigationTools 래핑
            for (java.lang.reflect.Method m : HrNavigationTools.class.getDeclaredMethods()) {
                if (m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                    wrappedTools.add(MethodToolCallback.builder()
                            .toolDefinition(MethodToolCallback.toolDefinition(navigationTools, m))
                            .toolMethod(navigationTools, m)
                            .build());
                }
            }
            // HrActionTools 래핑 (복잡하므로 간단히 직접 정의 가능하나 여기선 기존 빈 사용 방식 유지하되 결과만 인터셉트하도록 유도)
        } catch (Exception e) { log.error("Tool wrapping failed", e); }

        // 4. LLM 응답 스트림
        Flux<Map<String, Object>> llmFlux = chatClient.prompt()
                .tools(actionTools, navigationTools) // 여기선 직접 주입하되, 결과 파싱 로직을 강화함
                .user(u -> u.text("당신은 유능한 채용 비서입니다. 한국어로만 친절하게 대답하세요.\n" +
                                "- **금기**: `[APPROVAL]`, `[COMPLETED]` 등 기계적 태그를 직접 적지 마세요. 도구가 반환한 결과만 활용하세요.\n" +
                                "- **일괄 메일**: 대상자가 여러 명이면 `sendBulkResultEmail` 도구를 호출하세요.\n\n" +
                                "[참고]\n" + context + "\n[대화 맥락]\n" + history + "\n[질문]\n" + question))
                .stream().chatResponse()
                .map(response -> {
                    String chunk = "";
                    String finishReason = null;
                    if (response.getResult() != null) {
                        chunk = (response.getResult().getOutput() != null) ? response.getResult().getOutput().getText() : "";
                        finishReason = response.getResult().getMetadata().getFinishReason();
                    }
                    if (chunk == null) chunk = "";

                    Map<String, Object> event = new HashMap<>();
                    
                    // [설계 변경] LLM이 뱉는 텍스트에 포함된 툴 결과물을 실시간으로 채널링
                    if (chunk.contains("[APPROVAL:")) {
                        event.put("approval", chunk.trim());
                    } else if (chunk.contains("[COMPLETED:")) {
                        event.put("completed", chunk.trim());
                    } else if (chunk.contains("화면으로 이동합니다") || chunk.contains("완료했습니다")) {
                        event.put("system", chunk.trim());
                    } else {
                        event.put("text", chunk);
                    }

                    fullAnswer.append(chunk);
                    if (finishReason != null && !finishReason.isEmpty()) {
                        chatMessageRepository.save(ChatMessage.builder().userId(userId).role("ASSISTANT").content(fullAnswer.toString()).build());
                    }
                    return event;
                });

        return llmFlux;
    }

    public List<ChatMessage> getChatHistory(String userId) { return chatMessageRepository.findRecentMessagesAsc(userId, 50); }
    @org.springframework.transaction.annotation.Transactional
    public void clearChatHistory(String userId) { chatMessageRepository.deleteByUserId(userId); }
}
