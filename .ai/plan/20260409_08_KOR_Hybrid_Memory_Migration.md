# 하이브리드 RAG & 장기 기억(Long-term Memory) 구현 계획

기존의 강력한 **LangChain4j 기반 RAG 엔진**과 형의 본진 프로젝트 환경인 **Spring AI 1.1.2 기반의 대화 관리**를 결합하여, 과거 대화를 모두 기억하는 지능형 채용 비서를 구축합니다.

## User Review Required

> [!IMPORTANT]
> - **의존성 추가**: Spring AI 1.1.x 호환 스타터들을 추가합니다.
> - **JDBC 저장소**: `SPRING_AI_CHAT_MEMORY` 테이블이 DB에 생성되어 대화 로그를 저장합니다.
> - **유저 식별**: 고정된 유저 ID(`HR_USER_01`)를 대화 맥락의 키값으로 사용합니다.

## Proposed Changes

### [Infrastructure & Dependencies]

#### [MODIFY] [pom.xml](file:///home/foenix/workspace/spring-rag/pom.xml)
- Spring Boot 버전을 `3.2.x`로 업그레이드 (Spring AI 1.1.x 호환용).
- Spring AI BOM 및 관련 스타터 추가:
  - `spring-ai-openai-spring-boot-starter`
  - `spring-ai-jdbc-chat-memory-spring-boot-starter`

#### [NEW] [AiConfig.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/config/AiConfig.java)
- Spring AI의 `ChatClient`와 `JdbcChatMemoryRepository`를 Bean으로 등록합니다.
- `HR_USER_01`의 맥락 유지를 위한 `ChatMemory` 설정을 수행합니다.

### [Service Logic (Hybrid)]

#### [MODIFY] [RagService.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java)
- **검색(Retrieval)**: 기존 LangChain4j + ONNX 로직을 유지하여 관련 자소서를 찾아옵니다.
- **생성(Generation)**: 찾아온 데이터를 Spring AI의 `ChatClient`에 전달합니다.
- **기억(Memory)**: `ChatClient`에 `MessageChatMemoryAdvisor`를 장착하여 검색된 자소서 내용 + 이전 대화 내용을 합쳐서 답변을 생성합니다.

#### [MODIFY] [application.yml](file:///home/foenix/workspace/spring-rag/src/main/resources/application.yml)
- Spring AI용 OpenAI 설정(SiliconFlow URL/Key)을 추가합니다.

## Open Questions
- **DB 스키마 자동 생성**: `spring.ai.chat.memory.repository.jdbc.initialize-schema=always` 설정을 통해 테이블을 자동 생성할지, 아니면 수동으로 생성할지 결정이 필요합니다.

## Verification Plan

### Automated Tests
- `curl /api/chat -d '{"message": "안녕 난 소현이야"}'`
- `curl /api/chat -d '{"message": "내가 누구라고?"}'` -> 답변에 "소현"이 포함되는지 확인.
- DB `SPRING_AI_CHAT_MEMORY` 테이블에 로그가 쌓이는지 쿼리로 확인.

### Manual Verification
- 브라우저 채팅창에서 여러 번 질문을 던져 앞서 언급한 지원자 정보를 계속 기억하는지 테스트.
