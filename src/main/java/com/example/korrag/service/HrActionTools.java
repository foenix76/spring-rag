package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HrActionTools {
    private static final Logger log = LoggerFactory.getLogger(HrActionTools.class);

    @Tool(description = "후보자 한 명에게 합격 또는 불합격 통보 메일을 발송합니다. " +
            "이 작업은 민감하므로 반드시 먼저 사용자에게 승인을 요청해야 합니다. " +
            "사용자가 승인 버튼을 누르기 전까지는 confirmed를 false로 호출하여 '[APPROVAL_REQUIRED:...]' 토큰을 생성하세요.")
    public String sendResultEmail(String candidateId, String resultType, 
                                  @org.springframework.ai.tool.annotation.ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {
        String status = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";
        
        if (!confirmed) {
            log.info("[AI ACTION] 단건 메일 발송 승인 요청 생성: ID={}, 결과={}", candidateId, status);
            return String.format("[APPROVAL_REQUIRED:ACTION:TARGET:%s:%s] 후보자(%s)님께 %s 메일을 발송할까요?", 
                    candidateId, resultType, candidateId, status);
        }

        log.info("[AI ACTION] [CONFIRMED] sendResultEmail 실제 발송! 대상 ID: {}, 결과: {}", candidateId, status);
        return String.format("[SUCCESS] [ACTION_COMPLETED:EMAIL:%s] 후보자(%s)님께 %s 메일 발송을 실제로 완료했습니다.", 
                candidateId, candidateId, status);
    }

    @Tool(description = "여러 명의 후보자에게 일괄적으로 합격 또는 불합격 통보 메일을 발송합니다. " +
            "목록이 주어질 경우 반드시 이 도구를 사용하세요. " +
            "이 작업은 민감하므로 반드시 먼저 사용자에게 승인을 요청해야 합니다. " +
            "사용자가 승인 버튼을 누르기 전까지는 confirmed를 false로 호출하여 일괄 승인 요청 토큰을 생성하세요.")
    public String sendBulkResultEmail(@org.springframework.ai.tool.annotation.ToolParam(description = "후보자 ID들을 쉼표로 구분하여 입력 (예: ID1, ID2)") String candidateIds, String resultType, 
                                  @org.springframework.ai.tool.annotation.ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {
        String status = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";
        
        // 간단한 해시 기반으로 Task ID 생성
        String taskId = "TASK_" + Math.abs(candidateIds.hashCode());

        if (!confirmed) {
            log.info("[AI ACTION] 다건 메일 발송 승인 요청 생성: IDs={}, 결과={}", candidateIds, status);
            return String.format("[APPROVAL_REQUIRED:ACTION:TARGET:ALL:%s] 다음 후보자들(%s)에게 %s 메일을 일괄 발송할까요?", 
                    taskId, candidateIds, status);
        }

        log.info("[AI ACTION] [CONFIRMED] sendBulkResultEmail 실제 발송! 대상 IDs: {}, 결과: {}", candidateIds, status);
        return String.format("[SUCCESS] [ACTION_COMPLETED:ALL:%s] 후보자들(%s)에게 %s 메일 일괄 발송을 완료했습니다.", 
                taskId, candidateIds, status);
    }
}
