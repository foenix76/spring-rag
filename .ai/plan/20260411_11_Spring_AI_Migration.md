# KOR-RAG 프로젝트: Spring AI 통합 및 MCP STREAMABLE 적용 마이그레이션 계획

## 1. 개요 (Overview)
기존 RAG 엔진이었던 **LangChain4j**를 완전히 덜어내고, 챗봇의 핵심 두뇌(ChatClient)부터 행동 강령(MCP Server)까지 모두 **Spring AI 1.1.2** 단일 생태계로 통일하는 계획입니다.
이를 통해 기존의 커스텀 RAG 로직은 그대로 유지하면서, 능동적으로 서버 툴(화면 이동, 메일 발송)을 호출하는 진정한 의미의 **Agent**로 진화합니다.

---

## 2. 작업 상세 내역 (Implementation Steps)

### Phase 1: 의존성 정리 (pom.xml)
- **제거**: `langchain4j`, `langchain4j-open-ai`, `langchain4j-ollama`
- **추가**: 
  - `spring-ai-openai-spring-boot-starter` (SF-TEST 등 OpenAI 호환 API 사용을 위함)
  - `spring-ai-ollama-spring-boot-starter` (로컬 Ollama 사용 시)
- **유지**: RAG 임베딩(onnxruntime, djl) 및 MCP Server(`spring-ai-starter-mcp-server-webmvc` 1.1.2)

### Phase 2: 설정 파일 통합 (application.yml)
- 기존 LangChain4j 커스텀 프로퍼티(`app.llm.sf.token` 등)를 Spring AI 표준 설정(`spring.ai.openai.api-key`, `spring.ai.openai.base-url`)으로 맵핑하여 자동 설정(Auto-configuration)의 혜택을 극대화합니다.
- `STREAMABLE` 기반의 MCP 서버 설정은 기존대로 100% 유지합니다.

### Phase 3: 핵심 비즈니스 로직 개편 (RagService.java)
- 기존의 수동 LangChain4j `ChatModel` 생성 로직을 제거하고, Spring AI의 **`ChatClient`**를 주입받아 사용합니다.
- 기존의 RAG 검색 로직(`vectorRepository.searchSimilar`)은 완벽하게 호환되므로, 검색된 컨텍스트를 Spring AI의 `SystemPromptTemplate`에 주입하는 방식으로 재설계합니다.
- **툴 연동 핵심**: `ChatClient` 호출 시 `.tools(hrNavigationTools, hrActionTools)`를 명시하여, LLM이 답변 생성 전에 언제든 인사 시스템 도구를 호출할 수 있도록 강력하게 결합합니다.

### Phase 4: 어노테이션 재정렬 (Tools)
- 기존에 MCP 전용으로 변경했던 `org.springaicommunity...` 패키지 어노테이션을, Spring AI 내부 `ChatClient`와 MCP 서버 양쪽에서 모두 인식할 수 있는 최신 표준 `@Tool` 어노테이션으로 정비합니다.

---

## 3. 검증 계획 (Verification & Testing)

1.  **서버 기동 확인**: `STREAMABLE` 기반의 MCP 서버가 8091 포트로 에러 없이 정상 기동되는지 확인합니다.
2.  **RAG 기반 툴 호출 테스트 (API)**:
    - 요청: "서울대 출신 지원자 합격 메일 보내줘"
    - 기대 결과: 
      1. RAG 로직이 지원자 목록에서 '서울대 출신'의 ID를 검색합니다.
      2. Spring AI `ChatClient`가 검색된 ID를 바탕으로 `sendResultEmail` 툴을 실행합니다.
      3. 서버 콘솔에 `[AI ACTION]` 메일 발송 로그가 출력됩니다.
      4. 사용자에게 최종 성공 답변이 반환됩니다.
3.  **MCP 엔드포인트 응답**: 클라이언트가 `/mcp`로 `STREAMABLE` 연결 시, 내부 툴들이 정상적으로 노출되는지 최종 점검합니다.
