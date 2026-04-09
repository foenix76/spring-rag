# SiliconFlow(SF) 모델 제공자 추가 구현 계획

SiliconFlow에서 제공하는 Qwen 모델을 사용할 수 있도록 `SF-TEST` 프로바이더 설정을 추가합니다. SiliconFlow는 OpenAI 호환 API를 제공하므로 `OpenAiChatModel`을 활용합니다.

## Proposed Changes

### [Backend] Spring Boot 설정 및 서비스 수정

#### [MODIFY] [application.yml](file:///home/foenix/workspace/spring-rag/src/main/resources/application.yml)
- `app.llm.sf` 설정을 추가하여 SiliconFlow 관련 토큰과 모델명을 관리합니다.
- `SF_TOKEN` 환경변수를 통해 토큰을 주입받을 수 있도록 설정합니다.
- 기본 모델로 `Qwen/Qwen2.5-7B-Instruct`를 사용합니다.

#### [MODIFY] [RagService.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java)
- 생성자 파라미터에 SiliconFlow 관련 설정(`@Value`)을 추가합니다.
- `switch` 문에 `SF-TEST` 케이스를 추가하여 `OpenAiChatModel`을 생성하도록 구현합니다.
- SiliconFlow의 API 엔드포인트(`https://api.siliconflow.com/v1`)를 지정합니다.

---

## Verification Plan

### Automated Tests
- 애플리케이션 실행 시 `app.llm.provider`를 `SF-TEST`로 설정하고 로그에 `LLM 모드: SF-TEST`가 출력되는지 확인합니다.
- 실제로 질문을 던졌을 때(RAG 동작 시) SiliconFlow API를 호출하여 답변을 정상적으로 받아오는지 확인합니다.

### Manual Verification
- 환경변수 `SF_TOKEN`이 올바르게 설정되었는지 확인합니다.
- `application.yml`의 `provider`를 `SF-TEST`로 변경하여 기동 테스트를 진행합니다.
