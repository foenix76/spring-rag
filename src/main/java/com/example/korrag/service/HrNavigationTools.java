package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HrNavigationTools {
    private static final Logger log = LoggerFactory.getLogger(HrNavigationTools.class);

    public record NavigationResult(String target, String url, String message) {}

    @Tool(description = "인사 시스템의 특정 화면으로 이동합니다. 대상은 candidate_list(후보자 목록), schedule(면접 일정), report(통계 보고서), job_postings(채용공고) 중 하나여야 합니다.")
    public NavigationResult navigateTo(String target) {
        log.info("[AI ACTION] navigateTo 툴 호출됨! 대상: {}", target);
        
        String url;
        String message;

        switch (target) {
            case "candidate_list":
                url = "/candidates";
                message = "[시스템 지침]: 후보자 목록 화면으로 이동합니다. [NAVIGATE:/candidates]";
                break;
            case "schedule":
                url = "/interviews";
                message = "[시스템 지침]: 면접 일정 화면으로 이동합니다. [NAVIGATE:/interviews]";
                break;
            case "report":
                url = "/statistics";
                message = "[시스템 지침]: 채용 통계 보고서 화면으로 이동합니다. [NAVIGATE:/statistics]";
                break;
            case "job_postings":
                url = "/jobs";
                message = "[시스템 지침]: 채용공고 관리 화면으로 이동합니다. [NAVIGATE:/jobs]";
                break;
            default:
                url = "/dashboard";
                message = "[시스템 지침]: 대시보드로 이동합니다. [NAVIGATE:/dashboard] (알 수 없는 대상: " + target + ")";
        }

        return new NavigationResult(target, url, message);
    }
}
