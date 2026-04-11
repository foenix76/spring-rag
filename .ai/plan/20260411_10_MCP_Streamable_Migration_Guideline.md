# 🚀 KOR-RAG 프로젝트: MCP STREAMABLE 전환 및 에이전트 도구 통합 가이드라인

## 1. 개요 (Overview)
본 가이드라인은 현재 **LangChain4j** 기반의 단순 검색(RAG) 시스템인 `spring-rag` 프로젝트를 **Spring AI MCP** 기반의 능동형 에이전트 시스템으로 진화시키기 위한 기술적 로드맵을 제시한다. 

가장 큰 변화는 **"단순히 답변하는 챗봇"**에서 오늘 `stock-master`에서 입증된 **"직접 서버 로직을 실행하고 화면을 조작하는 에이전트"**로의 전환이다.

---

## 2. 인프라 업그레이드 (Phase 1)

현재 `Spring Boot 3.0.5` 환경은 최신 MCP(특히 STREAMABLE)와 Spring AI 기능을 온전히 활용하기에 한계가 있다.

### 2.1 버전 업그레이드 권고
- **추천**: **Spring Boot 3.4.x 이상** (Java 17 환경 유지 시) 또는 **4.0.4** (Java 21 환경 전향 시)
- **이유**: `Streamable HTTP` 전송 계층은 Spring AI 1.1.0(Boot 3.4 대응) 이상에서 안정화됨.

### 2.2 의존성 추가 (pom.xml)
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.2</version> <!-- Boot 3.4 이상 기준 -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- MCP Server WebMVC 스타터 (STREAMABLE 지원) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>
</dependencies>
```

---

## 3. STREAMABLE 프로토콜 설정 (Phase 2)

SSE의 버퍼링 이슈(0-byte 현상)를 방지하기 위해, `stock-master`에서 검증된 **STREAMABLE** 설정을 적용한다.

### 3.1 application.yml 설정
```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: HR-Recruitment-Hub-Agent
        version: 2.0.0
        protocol: STREAMABLE       # SSE 대신 STREAMABLE 사용
        request-timeout: 60s
      streamable-http:
        mcp-endpoint: /mcp         # 단일 통합 엔드포인트
```

---

## 4. 에이전트 도구(Tool) 구현 (Phase 3)

사용자의 말을 '행동'으로 옮기기 위한 핵심 도구들을 구현한다.

### 4.1 화면 이동 도구 (Navigation Tool)
사용자가 "합격자 목록 보여줘"라고 했을 때, Vue3 라우터를 조작할 수 있는 파라미터를 반환한다.

```java
@Component
public class hrNavigationTools {
    @Tool(description = "인사 시스템의 특정 화면으로 이동합니다.")
    public NavigationResult navigateTo(
            @Parameter(description = "이동할 대상(candidate_list, schedule, report)") String target) {
        String url = target + ".do"; // 레거시 호환 또는 Vue Router 매핑
        return new NavigationResult(target, url);
    }
}
```

### 4.2 인사 행위 도구 (Action Tool)
실제 메일을 발송하거나 DB 상태를 변경한다. (HITL-승인 절차 권장)

```java
@Component
public class hrActionTools {
    @Tool(description = "후보자에게 합격/불합격 통보 메일을 발송합니다.")
    public String sendResultEmail(String candidateId, String resultType) {
        // 실제 메일 발송 로직 또는 로그 출력
        return String.format("[SUCCESS] 후보자(%s)에게 %s 메일을 발송했습니다.", candidateId, resultType);
    }
}
```

---

## 5. 성공적인 시연을 위한 팁 (WOW Point)

1.  **복합 실행 (Agentic Loop)**: "서울대 출신 중 GSAT 90점 이상인 사람 합격시키고 메일 보내"라고 시켰을 때, 에이전트가 `검색(RAG)` -> `추천(LLM)` -> `상태변경(Tool)` -> `메일발송(Tool)`을 연속으로 수행하는 모습을 로그로 보여줄 것.
2.  **보안 강조 (HITL)**: "중요한 작업은 관리자의 [승인] 버튼 클릭 후 실행되도록 설계되었습니다"라고 설명하여 기술적 성숙도 어필.
3.  **로그 가시화**: MCP 실행 시 서버 콘솔이나 UI상에 **[AI ACTION]** 로그가 실시간으로 찍히게 하여 '살아있는 에이전트' 느낌 부여.

---
**작성일**: 2026-04-11
**작성자**: Antigravity (Assistant)
**관련 프로젝트**: [stock-master SUCCESS STORY]
