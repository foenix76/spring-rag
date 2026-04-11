# 안전 제일: 장기 기억(Long-term Memory) 구현 계획 (SB 3.0.5 고정)

Spring Boot 3.0.5 환경을 유지하면서, **JPA 영속성 계층**을 활용하여 `HR_USER_01`의 대화 맥락을 유지하는 기능을 구현합니다.

## User Review Required

> [!IMPORTANT]
> - **버전 유지**: 프로젝트 부모 버전(`3.0.5`)을 절대 변경하지 않습니다.
> - **DB 스키마**: `test.chat_messages` 테이블을 신규 생성합니다.
> - **메모리 방식**: DB에서 최근 N개의 대화를 가져와 프롬프트에 포함하는 방식으로 맥락을 제공합니다.

## Proposed Changes

### [Persistence Layer]

#### [NEW] [ChatMessage.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/entity/ChatMessage.java)
- 대화 로그를 저장하기 위한 JPA 엔티티입니다.
- 필드: `id`, `userId`, `role` (USER/AI), `content`, `createdAt`.

#### [NEW] [ChatMessageRepository.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/repository/ChatMessageRepository.java)
- `userId`별로 최근 대화 목록을 조회하는 기능을 포함합니다.

### [Service Logic Enhancement]

#### [MODIFY] [RagService.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java)
- `ask` 메서드가 `userId`를 받도록 수정합니다 (기본값: `HR_USER_01`).
- **History Fetch**: LLM 호출 전 DB에서 해당 유저의 최근 대화 내역을 가져옵니다.
- **Context Construction**: [RAG 검색 결과] + [이전 대화 내역] + [현재 질문]을 조합하여 프롬프트를 생성합니다.
- **Log Saving**: 질문과 답변을 비동기 또는 동기적으로 DB에 저장합니다.

### [API Layer]

#### [MODIFY] [ChatController.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/controller/ChatController.java)
- 클라이언트로부터 `userId`를 받거나, 없을 경우 `HR_USER_01`로 고정하여 `RagService`를 호출합니다.

## Open Questions
- **맥락 수**: 최근 몇 개의 대화까지 메모리로 유지할까요? (기본 제안: 최근 10개)

## Verification Plan

### Automated Tests
- 직접 SQL로 `test.chat_messages` 테이블 생성 확인.
- 동일 유저(`HR_USER_01`)로 연속 질문 시 DB에 데이터가 쌓이는지 확인.
- 서버 재시작 후에도 AI가 이전 대화 내용을 기억하여 답변하는지 확인.

### Manual Verification
- 프론트엔드 채팅 UI에서 "내 이름은 소현이야" 호출 후 "내 이름이 뭐지?"라고 다시 물어보기.
