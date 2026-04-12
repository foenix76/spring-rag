package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HrActionTools - HR 액션 도구 (메일 발송 HITL)
 *
 * [리턴 규격]
 * 구조적 토큰(APPROVAL 등)을 텍스트로 리턴하지 않습니다!
 * 대신 ToolEventPublisher를 통해 SSE Sink로 직접 이벤트를 전송하여(채널 분리),
 * RagService가 텍스트를 파싱하는 행위를 완전히 차단합니다.
 */
@Component
public class HrActionTools {
    private static final Logger log = LoggerFactory.getLogger(HrActionTools.class);
    
    private final ToolEventPublisher toolEventPublisher;
    
    public HrActionTools(ToolEventPublisher toolEventPublisher) {
        this.toolEventPublisher = toolEventPublisher;
    }

    @Tool(description = """
            지원자 한 명에게 합격(PASS) 또는 불합격(FAIL) 통보 메일을 발송합니다.
            이 작업은 민감하므로 반드시 먼저 사용자 승인을 요청해야 합니다.
            confirmed=false로 먼저 호출하여 승인 요청을 생성하세요.
            사용자가 명시적으로 '승인'한다는 메시지를 보낸 경우에만 confirmed=true로 재호출하세요.
            """)
    public String sendResultEmail(
            @ToolParam(description = "필수: 현재 요청을 수행하는 사용자의 ID. 항상 'HR_USER_01'을 입력하세요.") String userId,
            @ToolParam(description = "지원자 ID 또는 지원번호 (예: ACCEPT_001)") String candidateId,
            @ToolParam(description = "결과 유형: PASS(합격) 또는 FAIL(불합격)") String resultType,
            @ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {

        String statusKor = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";

        if (!confirmed) {
            log.info("[AI ACTION] 단건 메일 승인 요청: candidateId={}, result={}", candidateId, statusKor);
            String token = String.format("[APPROVAL:EMAIL:%s:%s]", candidateId, resultType.toUpperCase());
            toolEventPublisher.publishEvent(userId, java.util.Map.of("approval", token));
            return String.format("지원자(%s)님께 %s 결과 메일 발송을 위한 승인 요청 카드를 사용자 화면에 표시했습니다. 승인을 대기합니다.", candidateId, statusKor);
        }

        // 실제 발송 처리 (실제 환경에서는 메일 서비스 호출)
        log.info("[AI ACTION] [CONFIRMED] sendResultEmail 실행: candidateId={}, result={}", candidateId, statusKor);
        String token = String.format("[COMPLETED:EMAIL:%s]", candidateId);
        toolEventPublisher.publishEvent(userId, java.util.Map.of("completed", token));
        return String.format("지원자(%s)님께 %s 결과 메일을 성공적으로 발송 완료했습니다.", candidateId, statusKor);
    }

    @Tool(description = """
            [필수 도구] 여러 명의 지원자에게 메일을 일괄 발송해야 할 때 무조건 사용해야 하는 기능입니다.
            텍스트로 "승인 카드를 띄워드립니다"라고 말만 하지 말고, 반드시 이 도구를 **직접 실행**하여 approval 이벤트를 발생시키세요.
            confirmed=false로 먼저 호출하여 일괄 승인 요청을 생성하세요.
            사용자가 명시적으로 '승인'한다는 메시지를 보낸 경우에만 confirmed=true로 재호출하세요.
            """)
    public String sendBulkResultEmail(
            @ToolParam(description = "필수: 현재 요청을 수행하는 사용자의 ID. 항상 'HR_USER_01'을 입력하세요.") String userId,
            @ToolParam(description = "지원자 ID 또는 지원번호 목록 배열 (예: [\"ID001\", \"ID002\"])") String[] candidateIds,
            @ToolParam(description = "결과 유형: PASS(합격) 또는 FAIL(불합격)") String resultType,
            @ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {

        String statusKor = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";
        String joinedIds = String.join(", ", candidateIds);
        // 태스크 ID: 대상 IDs 해시 기반으로 고유 생성
        String taskId = "BULK_" + Integer.toHexString(Math.abs(joinedIds.hashCode())).toUpperCase();

        if (!confirmed) {
            log.info("[AI ACTION] 일괄 메일 승인 요청: taskId={}, candidates=[{}], result={}", taskId, joinedIds, statusKor);
            String token = String.format("[APPROVAL:BULK:%s]", taskId);
            toolEventPublisher.publishEvent(userId, java.util.Map.of("approval", token));
            return String.format("총 %d명의 지원자(%s)에게 %s 메일을 일괄 발송하기 위한 승인 요청 카드를 사용자 화면에 표시했습니다. 승인을 대기합니다.", candidateIds.length, joinedIds, statusKor);
        }

        log.info("[AI ACTION] [CONFIRMED] sendBulkResultEmail 실행: taskId={}, candidates=[{}]", taskId, joinedIds);
        String token = String.format("[COMPLETED:BULK:%s]", taskId);
        toolEventPublisher.publishEvent(userId, java.util.Map.of("completed", token));
        return String.format("총 %d명의 지원자(%s)에게 %s 결과 메일을 일괄 발송 완료했습니다.", candidateIds.length, joinedIds, statusKor);
    }
}
