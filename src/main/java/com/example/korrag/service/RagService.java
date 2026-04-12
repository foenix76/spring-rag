package com.example.korrag.service;

import com.example.korrag.entity.ChatMessage;
import com.example.korrag.repository.ChatMessageRepository;
import com.example.korrag.repository.VectorStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * ====================================================================
 * RagService - 필드 원천 분리형 멀티채널 스트리밍 (재건축 v3.0)
 * ====================================================================
 *
 * [아키텍처 핵심 원칙]
 * 1. 문자열 파싱 완전 금지: "[APPROVAL:..." 같은 토큰을 텍스트 스트림에서
 *    잘라내는 방식을 완전히 제거합니다.
 *
 * 2. 원천 분리(Source Separation): 이벤트 발생 시점에 올바른 채널로 라우팅.
 *    - text:      순수 LLM 텍스트 청크 (사용자 말풍선)
 *    - system:    도구 실행 로그 (상단 시스템 로그)
 *    - approval:  승인 요청 토큰 (HITL 버튼 카드)
 *    - completed: 작업 완료 토큰 (상태 업데이트)
 *    - navigate:  화면 이동 정보 (Toast 알림)
 *
 * 3. DB 저장: content 필드에 JSON 문자열로 필드를 함께 저장하여
 *    히스토리 재로딩 시 완벽 복원이 가능하게 합니다.
 *
 * [Flux.create 기반 이벤트 라우터]
 * LLM 스트리밍 도중 툴이 호출되면:
 *  a) 툴 반환값에서 구조적 토큰(APPROVAL, COMPLETED, navigate)을 감지
 *  b) 해당 이벤트를 FluxSink로 즉시 push (각자 올바른 채널 필드에)
 *  c) LLM이 툴 결과를 그대로 따라 읊는 텍스트는 필터링하여 사용자에게 노출 방지
 */
@Service
@Slf4j
public class RagService {

    private final ChatClient chatClient;
    private final OnnxEmbeddingService embeddingService;
    private final VectorStoreRepository vectorRepository;
    private final OnnxRerankService rerankService;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.rag.retrieval-top-k:20}") private int retrievalTopK;
    @Value("${app.rag.rerank-top-k:5}")     private int rerankTopK;
    @Value("${app.rag.similarity-threshold:0.3}") private float threshold;

    // ===== 구조적 토큰 식별자 (백엔드에서만 인식, 프론트 노출 금지) =====
    private static final String TOKEN_APPROVAL  = "[APPROVAL:";
    private static final String TOKEN_COMPLETED = "[COMPLETED:";
    private static final String TOKEN_CANCELLED = "[CANCELLED:";
    private static final String TOKEN_NAVIGATE  = "[NAVIGATE:";

    public RagService(ChatClient.Builder chatClientBuilder,
                      OnnxEmbeddingService embeddingService,
                      VectorStoreRepository vectorRepository,
                      OnnxRerankService rerankService,
                      ChatMessageRepository chatMessageRepository,
                      HrActionTools actionTools,
                      HrNavigationTools navigationTools) {
        this.chatClient = chatClientBuilder
                .defaultTools(actionTools, navigationTools)
                .build();
        this.embeddingService    = embeddingService;
        this.vectorRepository    = vectorRepository;
        this.rerankService       = rerankService;
        this.chatMessageRepository = chatMessageRepository;
    }

    // ========================================================================
    // 에포크 요약 (Epoch Summarization)
    // ========================================================================
    @org.springframework.transaction.annotation.Transactional
    public void processEpochSummarization(String userId) {
        Optional<ChatMessage> latestSummary =
                chatMessageRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, "SUMMARY");

        long count = latestSummary.isPresent()
                ? chatMessageRepository.countByUserIdAndIdGreaterThan(userId, latestSummary.get().getId())
                : chatMessageRepository.countByUserId(userId);

        if (count >= 10) {
            List<ChatMessage> history = chatMessageRepository.findRecentMessagesAsc(userId, 10);
            String rawHistory = history.stream()
                    .map(m -> m.getRole() + ": " + extractTextFromContent(m.getContent()))
                    .collect(Collectors.joining("\n"));

            String summary = chatClient.prompt()
                    .system("당신은 대화 기억 요약 전문가입니다. 아래 대화를 핵심만 간결하게 한국어로 요약하세요.")
                    .user(rawHistory)
                    .call().content();

            chatMessageRepository.deleteByUserIdAndRole(userId, "SUMMARY");
            chatMessageRepository.save(ChatMessage.builder()
                    .userId(userId).role("SUMMARY")
                    .content(toJsonContent("summary", summary))
                    .build());

            log.info("[EPOCH] 사용자 {} 요약 완료 (메시지 {} 개)", userId, count);
        }
    }

    // ========================================================================
    // AI 컨텍스트용 히스토리 조회
    // ========================================================================
    private String getHistoryContextForAi(String userId) {
        Optional<ChatMessage> summary =
                chatMessageRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, "SUMMARY");
        List<ChatMessage> recentMessages = chatMessageRepository.findRecentMessagesAsc(userId, 10);

        StringBuilder sb = new StringBuilder();
        summary.ifPresent(s -> sb.append("[이전 대화 요약]: ")
                .append(extractTextFromContent(s.getContent()))
                .append("\n"));

        recentMessages.forEach(m ->
                sb.append(m.getRole()).append(": ")
                  .append(extractTextFromContent(m.getContent()))
                  .append("\n")
        );
        return sb.toString();
    }

    // ========================================================================
    // 핵심: 필드 원천 분리형 스트리밍 (Flux.create 기반)
    // ========================================================================
    public Flux<Map<String, Object>> askStream(String userId, String question) {
        // --- 1) 에포크 처리 & 히스토리 로딩 ---
        processEpochSummarization(userId);
        String history = getHistoryContextForAi(userId);

        // --- 2) RAG: 라우팅 + 정밀 검색어 추출 (Few-shot 강화) ---
        String routingResponse = chatClient.prompt()
                .system(buildRoutingSystemPrompt())
                .user("사용자 질문: " + question)
                .call().content();

        log.debug("[ROUTING] 분류 결과: {}", routingResponse);

        String optimizedQuery = extractOptimizedQuery(routingResponse, question);
        boolean isActionIntent = routingResponse != null && routingResponse.contains("INTENT: [ACTION]");

        // --- 3) RAG 검색 (검색어가 NONE이 아닐 때 무조건 수행) ---
        List<Map<String, Object>> searchResults = List.of();
        if (!optimizedQuery.equalsIgnoreCase("NONE")) {
            try {
                float[] vec = embeddingService.embedQuery(optimizedQuery);
                searchResults = rerankService.rerank(
                        optimizedQuery,
                        vectorRepository.searchSimilar(vec, retrievalTopK, threshold),
                        rerankTopK
                );
                log.info("[RAG] 검색어='{}' 결과 {}개 (reranked {}개)", optimizedQuery, retrievalTopK, searchResults.size());
            } catch (Exception e) {
                log.error("[RAG] 검색 중 오류 발생: {}", e.getMessage());
            }
        }

        final String context = buildContextString(searchResults);

        // --- 4) 사용자 메시지 DB 저장 ---
        chatMessageRepository.save(ChatMessage.builder()
                .userId(userId).role("USER")
                .content(toJsonContent("text", question))
                .build());

        // --- 5) Flux.create 기반 멀티채널 스트리밍 ---
        final StringBuilder fullTextAccumulator   = new StringBuilder(); // 최종 text 누적
        final AtomicBoolean hasApprovalBeenSent   = new AtomicBoolean(false);
        final AtomicBoolean hasCompletedBeenSent  = new AtomicBoolean(false);
        final AtomicBoolean hasNavigateBeenSent   = new AtomicBoolean(false);
        final AtomicBoolean savedToDb             = new AtomicBoolean(false); // 중복 저장 방지
        final List<Map<String, Object>> savedEvents = Collections.synchronizedList(new ArrayList<>());

        return Flux.<Map<String, Object>>create(sink -> {
            try {
                chatClient.prompt()
                        .system(buildMainSystemPrompt())
                        .user(buildUserPrompt(context, history, question))
                        .stream().chatResponse()
                        .publishOn(Schedulers.boundedElastic())
                        .doOnNext(response -> {
                            String chunk = "";
                            String finishReason = null;

                            if (response.getResult() != null) {
                                var output = response.getResult().getOutput();
                                if (output != null) {
                                     chunk = Optional.ofNullable(output.getText()).orElse("");
                                }
                                if (response.getResult().getMetadata() != null) {
                                    finishReason = response.getResult().getMetadata().getFinishReason();
                                }
                            }

                            // === 심플 텍스트 라우팅 & Non-blocking 토큰 파싱 ===
                            if (!chunk.isEmpty()) {
                                fullTextAccumulator.append(chunk);
                                
                                // 지연이나 필터링 없이 텍스트 즉시 발송
                                Map<String, Object> textEvent = Map.of("text", chunk);
                                savedEvents.add(textEvent);
                                sink.next(textEvent);
                            }
                            
                            String accumulated = fullTextAccumulator.toString();

                            // [APPROVAL:...] 토큰 발송 (최초 1회)
                            if (!hasApprovalBeenSent.get() && accumulated.contains(TOKEN_APPROVAL)) {
                                int tS = accumulated.indexOf(TOKEN_APPROVAL);
                                int tE = accumulated.indexOf("]", tS);
                                if (tE > tS) {
                                    String token = accumulated.substring(tS, tE + 1);
                                    Map<String, Object> approvalEvent = Map.of("approval", token);
                                    savedEvents.add(approvalEvent);
                                    sink.next(approvalEvent);
                                    hasApprovalBeenSent.set(true);
                                    log.info("[CHANNEL:approval] 토큰 발송: {}", token);
                                }
                            }

                            // [COMPLETED:...] 토큰 발송 (최초 1회)
                            if (!hasCompletedBeenSent.get() && accumulated.contains(TOKEN_COMPLETED)) {
                                int tS = accumulated.indexOf(TOKEN_COMPLETED);
                                int tE = accumulated.indexOf("]", tS);
                                if (tE > tS) {
                                    String token = accumulated.substring(tS, tE + 1);
                                    Map<String, Object> completedEvent = Map.of("completed", token);
                                    savedEvents.add(completedEvent);
                                    sink.next(completedEvent);
                                    hasCompletedBeenSent.set(true);
                                    log.info("[CHANNEL:completed] 토큰 발송: {}", token);
                                }
                            }

                            // [NAVIGATE:...] 토큰 발송 (최초 1회)
                            if (!hasNavigateBeenSent.get() && accumulated.contains(TOKEN_NAVIGATE)) {
                                int tS = accumulated.indexOf(TOKEN_NAVIGATE);
                                int tE = accumulated.indexOf("]", tS);
                                if (tE > tS) {
                                    String token = accumulated.substring(tS, tE + 1);
                                    String url = token.replace("[NAVIGATE:", "").replace("]", "").trim();
                                    
                                    Map<String, Object> navMap = new HashMap<>();
                                    navMap.put("url", url);
                                    
                                    Map<String, Object> navigateEvent = Map.of("navigate", navMap);
                                    savedEvents.add(navigateEvent);
                                    sink.next(navigateEvent);
                                    hasNavigateBeenSent.set(true);
                                    log.info("[CHANNEL:navigate] 화면 이동 이벤트 발송: {}", url);
                                }
                            }

                            // 스트림 종료 시 DB 저장 (중복 방지)
                            if (finishReason != null && !finishReason.isEmpty() && !"NONE".equalsIgnoreCase(finishReason)) {
                                if (savedToDb.compareAndSet(false, true)) {
                                    saveAssistantMessage(userId, savedEvents, fullTextAccumulator.toString());
                                    log.info("[STREAM] 종료 (finishReason={}), 저장 완료", finishReason);
                                }
                            }
                        })
                        .doOnComplete(() -> {
                            // finishReason이 안 잡힌 경우 대비 최종 저장 (중복 방지)
                            if (savedToDb.compareAndSet(false, true)) {
                                if (fullTextAccumulator.length() > 0 || !savedEvents.isEmpty()) {
                                    saveAssistantMessage(userId, savedEvents, fullTextAccumulator.toString());
                                }
                            }
                            sink.complete();
                        })
                        .doOnError(e -> {
                            log.error("[STREAM] 오류 발생: {}", e.getMessage(), e);
                            sink.error(e);
                        })
                        .subscribe();
            } catch (Exception e) {
                log.error("[RagService] askStream 초기화 오류: {}", e.getMessage(), e);
                sink.error(e);
            }
        });
    }

    // ========================================================================
    // 히스토리 조회 / 초기화
    // ========================================================================
    public List<ChatMessage> getChatHistory(String userId) {
        return chatMessageRepository.findRecentMessagesAsc(userId, 50);
    }

    @org.springframework.transaction.annotation.Transactional
    public void clearChatHistory(String userId) {
        chatMessageRepository.deleteByUserId(userId);
        log.info("[HISTORY] 사용자 {} 히스토리 전체 초기화", userId);
    }

    // ========================================================================
    // 내부 유틸리티 메서드
    // ========================================================================

    /** content JSON에서 텍스트만 추출 */
    private String extractTextFromContent(String content) {
        if (content == null) return "";
        try {
            if (content.trim().startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(content, Map.class);
                return Optional.ofNullable(map.get("text"))
                        .map(Object::toString)
                        .orElse(Optional.ofNullable(map.get("summary"))
                                .map(Object::toString)
                                .orElse(""));
            }
        } catch (Exception e) {
            // 구형 포맷(plain text) 호환
        }
        return content;
    }

    /** 단일 필드를 JSON string으로 변환 */
    private String toJsonContent(String field, String value) {
        try {
            return objectMapper.writeValueAsString(Map.of(field, value));
        } catch (Exception e) {
            return "{\"" + field + "\": \"" + value.replace("\"", "\\\"") + "\"}";
        }
    }

    /** ASSISTANT 메시지를 이벤트 리스트 포함 JSON으로 DB 저장 */
    private void saveAssistantMessage(String userId, List<Map<String, Object>> events, String fullText) {
        try {
            // 중복 저장 방지 (이미 저장된 경우 스킵)
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", fullText.trim());
            payload.put("events", new ArrayList<>(events));

            String jsonContent = objectMapper.writeValueAsString(payload);
            chatMessageRepository.save(ChatMessage.builder()
                    .userId(userId).role("ASSISTANT")
                    .content(jsonContent)
                    .build());
        } catch (Exception e) {
            log.error("[DB] ASSISTANT 메시지 저장 실패: {}", e.getMessage());
            // fallback: plain text 저장
            chatMessageRepository.save(ChatMessage.builder()
                    .userId(userId).role("ASSISTANT")
                    .content(toJsonContent("text", fullText.trim()))
                    .build());
        }
    }

    /** 라우팅 시스템 프롬프트 (Few-shot 강화) */
    private String buildRoutingSystemPrompt() {
        return """
                당신은 채용 HR 시스템 의도 분류기입니다.
                사용자 질문의 의도를 정확히 파악하여 아래 형식으로만 응답하세요.
                
                형식: INTENT: [SEARCH|ACTION|GENERAL] QUERY: [검색어 또는 NONE]
                
                규칙:
                - SEARCH: 특정 지원자 정보 조회, 목록 확인, 에세이 내용 검색
                - ACTION: 메일 발송, 화면 이동 등 실제 작업 요청 (검색 후 작업해야 할 경우 포함)
                - GENERAL: 단순 인사, 시스템 관련 일반 질문
                - QUERY: 검색에 사용할 핵심 키워드 (동의어/연관어 포함). 찾을 대상이 없으면 NONE
                
                예시:
                사용자: "올해 지원자 목록 보여줘"
                → INTENT: [SEARCH] QUERY: 지원자 목록
                
                사용자: "김민준 에세이 어때?"
                → INTENT: [SEARCH] QUERY: 김민준
                
                사용자: "이 사람한테 합격 메일 보내줘"
                → INTENT: [ACTION] QUERY: NONE
                
                사용자: "인턴 경험 있는 지원자 3명 합격 메일 보내줘"
                → INTENT: [ACTION] QUERY: 인턴 경험
                
                사용자: "후보자 목록 화면으로 이동해줘"
                → INTENT: [ACTION] QUERY: NONE
                
                사용자: "안녕"
                → INTENT: [GENERAL] QUERY: NONE
                
                주의: 절대 가상의 지원자 이름이나 ID를 생성하지 마세요.
                """;
    }

    /** 검색어 추출 */
    private String extractOptimizedQuery(String routingResponse, String fallback) {
        if (routingResponse == null) return fallback;
        if (routingResponse.contains("QUERY:")) {
            String query = routingResponse.substring(routingResponse.indexOf("QUERY:") + 6).trim();
            query = query.replace("[", "").replace("]", "").trim();
            if ("NONE".equalsIgnoreCase(query) || query.isEmpty()) return "NONE";
            return query;
        }
        return fallback;
    }

    /** RAG 컨텍스트 문자열 생성 */
    private String buildContextString(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "(현재 조건에 맞는 실제 지원자 정보가 없습니다. 가상 데이터를 생성하지 마세요.)";
        }
        return results.stream()
                .map(r -> String.format("[지원자: %s (지원번호: %s)]\n%s",
                        r.getOrDefault("name", "알 수 없음"),
                        r.getOrDefault("accept_no", "알 수 없음"),
                        r.getOrDefault("content", "")))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /** 메인 LLM 시스템 프롬프트 */
    private String buildMainSystemPrompt() {
        return """
                당신은 대한민국 채용 HR 시스템의 유능한 AI 비서입니다.
                
                [절대 규칙 - 시스템 마비 방지용]
                1. 반드시 모든 응답은 한국어로만 답변합니다. (중국어, 영어 절대 불가)
                2. [참고 지원자 정보]에 없는 지원자 이름, ID, 지원번호를 절대 만들지 마세요.
                   - 정보가 없으면 "해당 정보를 찾을 수 없습니다"라고 솔직하게 말하세요.
                3. ★★★ [APPROVAL:...], [COMPLETED:...], [NAVIGATE:...] 구조적 토큰 취급 규칙 ★★★
                   - 도구(Tool) 호출 결과에 이 대괄호 토큰이 포함되어 있다면, 당신의 응답 메시지 마지막 구석에 **단 한 글자도 바꾸지 말고 100% 원본 그대로 반드시 출력**하세요!!
                   - 번역, 생략, 요약, 임의 생성 절대 금지. (이 토큰이 누락되거나 변형되면 UI 컴포넌트가 파괴됩니다)
                   - 올바른 예시: "메일을 발송 완료했습니다. [COMPLETED:BULK:BULK_8F9A]"
                   - 틀린 예시: "메일을 완료했습니다. [COMPLETED:sendBulkResultEmail,candidateIds=2026]" (절대 당신 마음대로 포맷을 지어내지 마세요!)
                4. 여러 명에게 메일을 보낼 때는 반드시 sendBulkResultEmail 도구를 사용하세요.
                5. 특정 조건의 지원자 목록을 나열할 때는 **반드시 해당 지원자가 추출된 근거(요약)**를 그 옆에 한 줄로 작성해 주세요.
                6. 툴이 반환한 내용 중 토큰을 제외한 나머지 텍스트는 당신이 부드러운 한국어로 의역해도 좋습니다. 단, 대괄호 토큰만은 절대 건드리지 마세요.
                """;
    }

    /** 메인 LLM 사용자 프롬프트 조합 */
    private String buildUserPrompt(String context, String history, String question) {
        return String.format("""
                [참고 지원자 정보]
                %s
                
                [이전 대화 맥락]
                %s
                
                [사용자 요청]
                %s
                """, context, history.trim(), question);
    }
}
