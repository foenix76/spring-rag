package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HrActionTools {
    private static final Logger log = LoggerFactory.getLogger(HrActionTools.class);

    @Tool(description = "후보자에게 합격 또는 불합격 통보 메일을 발송합니다. candidateId(후보자ID)와 resultType(PASS 또는 FAIL)이 필요합니다.")
    public String sendResultEmail(String candidateId, String resultType) {
        String status = resultType.equalsIgnoreCase("PASS") ? "합격" : "불합격";
        
        log.info("[AI ACTION] sendResultEmail 툴 호출됨! 대상 ID: {}, 결과: {}", candidateId, status);
        
        return String.format("[SUCCESS] 후보자(%s)님께 %s 메일 발송을 완료했습니다.", candidateId, status);
    }
}
