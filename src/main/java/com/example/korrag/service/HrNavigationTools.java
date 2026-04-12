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
 * RagService에서 텍스트를 파싱하는 것을 방지하기 위해,
 * ToolEventPublisher를 통해 SSE Sink로 별도의 시스템 이벤트를 직접 쏩니다.
 */
@Component
public class HrNavigationTools {
    private static final Logger log = LoggerFactory.getLogger(HrNavigationTools.class);
    
    private final ToolEventPublisher toolEventPublisher;
    
    public HrNavigationTools(ToolEventPublisher toolEventPublisher) {
        this.toolEventPublisher = toolEventPublisher;
    }

    public record NavigationResult(String target, String url) {}

    @Tool(description = """
            [필수 도구] 사용자가 특정 화면으로 이동하거나 보여달라고 요청할 때 무조건 사용해야 하는 기능입니다.
            텍스트로 "이동합니다" 라고 말만 하지 말고, 반드시 이 도구를 **직접 실행**하여 브라우저 이벤트를 발생시키세요.
            가능한 이동 대상: dashboard(대시보드), candidate_list(지원자 목록), schedule(면접 일정), report(통계 보고서), job_postings(채용공고)
            """)
    public String navigateTo(
            @ToolParam(description = "필수: 현재 대화중인 사용자의 ID ('HR_USER_01')") String userId,
            @ToolParam(description = "이동할 화면: dashboard | candidate_list | schedule | report | job_postings") String target) {
        log.info("[AI ACTION] navigateTo 호출: target={}", target);

        String lowerTarget = target != null ? target.toLowerCase() : "";
        String menuName;
        String url;

        if (lowerTarget.contains("candidate") || lowerTarget.contains("지원자")) {
            menuName = "지원자 목록";
            url = "/candidates";
        } else if (lowerTarget.contains("schedule") || lowerTarget.contains("면접")) {
            menuName = "면접 일정";
            url = "/interviews";
        } else if (lowerTarget.contains("report") || lowerTarget.contains("통계")) {
            menuName = "채용 통계 보고서";
            url = "/statistics";
        } else if (lowerTarget.contains("job") || lowerTarget.contains("채용공고")) {
            menuName = "채용공고 관리";
            url = "/jobs";
        } else {
            menuName = "대시보드";
            url = "/dashboard";
        }

        // UI에 화면 이동 이벤트(URL 포함)를 직접 발송 (파싱 불필요)
        java.util.Map<String, Object> navData = new java.util.HashMap<>();
        navData.put("url", url);
        navData.put("message", menuName + " 화면으로 이동합니다.");
        
        toolEventPublisher.publishEvent(userId, java.util.Map.of("navigate", navData));

        // LLM에게는 화면 이동 처리가 되었다고 텍스트만 전달
        return String.format("사용자에게 %s 화면으로 이동하는 명령을 브라우저 시스템 채널로 성공적으로 전송했습니다.", menuName);
    }
}
