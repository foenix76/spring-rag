package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HrActionTools - HR 액션 도구 (메일 발송 HITL)
 *
 * [리턴 토큰 규격 - 백엔드 RagService가 감지하는 구조적 토큰]
 * - 미승인 상태: "...설명 텍스트... [APPROVAL:TYPE:ID_OR_TASKID]"
 * - 승인 완료:   "...설명 텍스트... [COMPLETED:TYPE:ID_OR_TASKID]"
 *
 * 중요: 이 토큰들은 RagService에서 가로채어 SSE 이벤트의 별도 필드로 라우팅됩니다.
 * LLM이 이 텍스트를 그대로 읊으면 안 됩니다 (시스템 프롬프트에서 금지).
 */
@Component
public class HrActionTools {
    private static final Logger log = LoggerFactory.getLogger(HrActionTools.class);

    @Tool(description = """
            후보자 한 명에게 합격(PASS) 또는 불합격(FAIL) 통보 메일을 발송합니다.
            이 작업은 민감하므로 반드시 먼저 사용자 승인을 요청해야 합니다.
            confirmed=false로 먼저 호출하여 승인 요청을 생성하세요.
            사용자가 명시적으로 '승인'한다는 메시지를 보낸 경우에만 confirmed=true로 재호출하세요.
            """)
    public String sendResultEmail(
            @ToolParam(description = "지원자 ID 또는 지원번호 (예: ACCEPT_001)") String candidateId,
            @ToolParam(description = "결과 유형: PASS(합격) 또는 FAIL(불합격)") String resultType,
            @ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {

        String statusKor = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";

        if (!confirmed) {
            log.info("[AI ACTION] 단건 메일 승인 요청: candidateId={}, result={}", candidateId, statusKor);
            // [APPROVAL:EMAIL:candidateId:resultType] 형식의 구조적 토큰 반환
            return String.format(
                    "후보자(%s)님께 %s 결과 메일 발송을 위해 승인이 필요합니다. [APPROVAL:EMAIL:%s:%s]",
                    candidateId, statusKor, candidateId, resultType.toUpperCase());
        }

        // 실제 발송 처리 (실제 환경에서는 메일 서비스 호출)
        log.info("[AI ACTION] [CONFIRMED] sendResultEmail 실행: candidateId={}, result={}", candidateId, statusKor);
        return String.format(
                "후보자(%s)님께 %s 결과 메일을 성공적으로 발송했습니다. [COMPLETED:EMAIL:%s]",
                candidateId, statusKor, candidateId);
    }

    @Tool(description = """
            여러 후보자에게 합격(PASS) 또는 불합격(FAIL) 통보 메일을 일괄 발송합니다.
            대상자가 2명 이상이면 반드시 이 도구를 사용하세요 (sendResultEmail 반복 금지).
            confirmed=false로 먼저 호출하여 일괄 승인 요청을 생성하세요.
            사용자가 명시적으로 '승인'한다는 메시지를 보낸 경우에만 confirmed=true로 재호출하세요.
            """)
    public String sendBulkResultEmail(
            @ToolParam(description = "후보자 ID 목록, 쉼표로 구분 (예: ID001, ID002, ID003)") String candidateIds,
            @ToolParam(description = "결과 유형: PASS(합격) 또는 FAIL(불합격)") String resultType,
            @ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {

        String statusKor = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";
        // 태스크 ID: 대상 IDs 해시 기반으로 고유 생성
        String taskId = "BULK_" + Integer.toHexString(Math.abs(candidateIds.hashCode())).toUpperCase();

        if (!confirmed) {
            log.info("[AI ACTION] 일괄 메일 승인 요청: taskId={}, candidates=[{}], result={}", taskId, candidateIds, statusKor);
            return String.format(
                    "다음 %d명의 후보자에게 %s 메일을 일괄 발송하려 합니다.\n대상: %s\n승인해 주시면 진행하겠습니다. [APPROVAL:BULK:%s]",
                    candidateIds.split(",").length, statusKor, candidateIds.trim(), taskId);
        }

        log.info("[AI ACTION] [CONFIRMED] sendBulkResultEmail 실행: taskId={}, candidates=[{}]", taskId, candidateIds);
        return String.format(
                "총 %d명의 후보자(%s)에게 %s 결과 메일을 일괄 발송 완료했습니다. [COMPLETED:BULK:%s]",
                candidateIds.split(",").length, candidateIds.trim(), statusKor, taskId);
    }
}
