# 🚀 KOR-RAG: Spring AI 기반 차세대 에이전트 전환 상세 설계서 (v1.2)

## 1. 개요 (Overview)
기존의 LangChain4j 기반 RAG 시스템을 **Spring AI 1.1.2** 생태계로 전면 통합합니다. 특히 **MCP STREAMABLE** 프로토콜을 활용하여 MVC 환경에서도 안정적인 에이전트 도구(Tool) 실행 환경을 구축하며, LLM이 직접 시스템 기능을 제어하는 **Actionable Agent**로 진화합니다.

---

## 2. 인프라 마이그레이션 전략 (Infra & Config)

### 2.1 의존성 (pom.xml) 핵심 포인트
- **Spring AI 1.1.2 정식 버전**: `STREAMABLE` 지원이 확실한 버전 사용.
- **Lombok & Maven Compiler**: Spring Boot 3.4+ 환경에서 롬복 어노테이션이 무시되지 않도록 `annotationProcessorPaths` 설정 완료.
- **OpenAI Starter**: SF-TEST(SiliconFlow) 등 OpenAI 호환 API 연동을 위해 사용.

### 2.2 설정 (application.yml) 최적화
- **설정 통합**: `spring.ai.openai` 하위로 API 키와 Base URL을 관리하여 `ChatModel` 자동 설정을 활용함.
- **MCP 최적화**: `protocol: STREAMABLE` 및 `mcp-endpoint: /mcp` 설정을 통해 단일 통합 엔드포인트 유지.

---

## 3. 핵심 비즈니스 로직 설계 (Business Logic)

### 3.1 RagService의 진화
- **ChatClient 주입**: `ChatModel`을 직접 다루지 않고, 유연한 프롬프트 구성을 위해 `ChatClient`를 활용함.
- **RAG 컨텍스트 주입**: 기존 벡터 검색 결과(`context`)를 System Prompt의 변수로 주입하여 지식 기반 답변 생성.
- **툴 바인딩 (Tool Binding)**: 
  - `HrNavigationTools`, `HrActionTools` 빈을 `ChatClient` 호출 시 동적으로 바인딩.
  - LLM이 질문의 의도를 분석하여 필요 시 자동으로 툴을 호출(Function Calling)함.

### 3.2 도구(Tool) 어노테이션 표준화
- **표준 어노테이션**: `@Tool` (`org.springframework.ai.tool.annotation.Tool`) 사용.
- **Mcp Server 호환**: Spring AI 1.1.2에서는 `@Tool`이 붙은 빈들을 MCP Server가 자동으로 감지하여 외부에 노출함.

---

## 4. 구체적 구현 시나리오 (Implementation Scenario)

### 4.1 "후보자 목록 보여줘" 요청 시
1.  사용자 입력 수신 (`ChatController` -> `RagService`).
2.  `RagService`가 `ChatClient` 호출.
3.  LLM이 `hrNavigationTools.navigateTo` 툴의 설명을 보고 호출 결정.
4.  서버 내부에서 툴 실행 -> 결과(`NavigationResult`) 반환.
5.  LLM이 툴 실행 결과를 포함하여 "알겠습니다. 후보자 목록 화면으로 안내해 드릴게요."라고 응답.

### 4.2 "합격 메일 보내" 요청 시
1.  RAG를 통해 대상 후보자 식별 (ID 추출).
2.  LLM이 `hrActionTools.sendResultEmail(candidateId, "PASS")` 호출.
3.  서버 로그에 `[AI ACTION]` 기록 및 실제 비즈니스 로직 수행.
4.  사용자에게 성공 메시지 반환.

---

## 5. 검증 및 테스트 계획 (Validation)

1.  **빌드 검증**: `mvn clean compile`을 통해 롬복 및 패키지 충돌 여부 확인.
2.  **기동 검증**: 8091 포트 기동 및 로그에서 `Registered tools: 2` 확인.
3.  **엔드포인트 테스트**: `/mcp` 경로가 `404` 없이 `400`(Accept 헤더 부족 등) 이상의 정상 반응을 하는지 확인.
4.  **실제 채팅 테스트**: "이름이 김철수인 지원자 찾아줘" -> "그 사람한테 합격 메일 보내줘" 시퀀스 작동 여부.

---

## 6. 특이사항 및 주의사항
- **SIGSEGV 방어**: 잦은 서버 재시작 시 ONNX 네이티브 자원 충돌 주의 (필요 시 서버 완전 종료 후 2~3초 대기).
- **패키지 경로**: Spring AI 1.1.2부터는 `org.springaicommunity` 대신 표준 `org.springframework.ai` 경로의 `@Tool` 사용이 권장됨 (구현 시 최종 확인).
