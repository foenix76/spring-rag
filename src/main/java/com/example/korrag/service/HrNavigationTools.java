package com.example.korrag.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HrNavigationTools - 화면 이동 도구
 *
 * [리턴 규격]
 * NavigationResult 레코드를 반환합니다.
 * RagService에서 이 결과를 system 채널(로그) + navigate 채널(Toast)로 분리 발송합니다.
 */
@Component
public class HrNavigationTools {
    private static final Logger log = LoggerFactory.getLogger(HrNavigationTools.class);

    public record NavigationResult(String target, String url, String message) {}

    @Tool(description = """
            HR 시스템 내 특정 화면으로 이동합니다.
            가능한 이동 대상: candidate_list(후보자 목록), schedule(면접 일정), report(통계 보고서), job_postings(채용공고)
            """)
    public NavigationResult navigateTo(
            @ToolParam(description = "이동할 화면: candidate_list | schedule | report | job_postings") String target) {
        log.info("[AI ACTION] navigateTo 호출: target={}", target);

        return switch (target) {
            case "candidate_list" -> new NavigationResult(target, "/candidates",  "[NAVIGATE:/candidates] 후보자 목록 화면으로 이동합니다.");
            case "schedule"       -> new NavigationResult(target, "/interviews",  "[NAVIGATE:/interviews] 면접 일정 화면으로 이동합니다.");
            case "report"         -> new NavigationResult(target, "/statistics",  "[NAVIGATE:/statistics] 채용 통계 보고서 화면으로 이동합니다.");
            case "job_postings"   -> new NavigationResult(target, "/jobs",        "[NAVIGATE:/jobs] 채용공고 관리 화면으로 이동합니다.");
            default               -> new NavigationResult(target, "/dashboard",   "[NAVIGATE:/dashboard] 대시보드로 이동합니다. (알 수 없는 대상: " + target + ")");
        };
    }
}
