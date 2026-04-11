package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HrActionTools {
    private static final Logger log = LoggerFactory.getLogger(HrActionTools.class);

    @Tool(description = "후보자에게 합격 또는 불합격 통보 메일을 발송합니다. " +
            "candidateId(후보자ID)와 resultType(PASS 또는 FAIL)이 필요합니다. " +
            "이 작업은 민감하므로 반드시 먼저 사용자에게 승인을 요청해야 하며, confirmed가 true인 경우에만 실제 발송됩니다.")
    public String sendResultEmail(String candidateId, String resultType, 
                                  @org.springframework.ai.tool.annotation.ToolParam(description = "사용자가 명시적으로 승인한 경우에만 true") boolean confirmed) {
        String status = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";
        
        if (!confirmed) {
            log.info("[AI ACTION] 메일 발송 승인 요청 생성: ID={}, 결과={}", candidateId, status);
            return String.format("[APPROVAL_REQUIRED:EMAIL:%s:%s] 후보자(%s)님께 %s 메일을 발송할까요?", 
                    candidateId, resultType, candidateId, status);
        }

        log.info("[AI ACTION] [CONFIRMED] sendResultEmail 실제 발송! 대상 ID: {}, 결과: {}", candidateId, status);
        return String.format("[SUCCESS] 후보자(%s)님께 %s 메일 발송을 실제로 완료했습니다.", candidateId, status);
    }
}
