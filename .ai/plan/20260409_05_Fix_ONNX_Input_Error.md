# ONNX 임베딩 모델 입력 오류(Expected [1,2) found 3) 해결 계획

`multilingual-e5-base` 모델의 ONNX 버전이 기대하는 입력 파라미터 개수와 현재 코드에서 전달하는 개수가 달라 발생하는 오류를 수정합니다.

## Research Findings

- 현재 코드: `input_ids`, `attention_mask`, `token_type_ids` 총 3개를 전달 중.
- 모델 분석: `strings` 명령어로 확인 결과 `token_type_ids`가 모델 내에 존재하지 않음.
- 오류 메시지(`expected [1,2)`) 분석: 모델이 단 1개의 입력(`input_ids`)만 기대하거나, 특정 버전의 변환 모델인 것으로 보입니다.

## Proposed Changes

### [Backend] OnnxEmbeddingService 수정

#### [MODIFY] [OnnxEmbeddingService.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/OnnxEmbeddingService.java)
- 모델 분석 결과에 맞춰 `token_type_ids`를 제거합니다.
- 오류 메시지가 `expected [1,2)`인 점을 고려하여, 우선 `attention_mask`도 제거하고 `input_ids`만 전달하도록 수정합니다. (만약 2개를 기대한다면 `expected [1,3)` 등으로 표시되는 것이 일반적입니다.)

---

## Verification Plan

### Automated Tests
- 수정 후 `mvn spring-boot:run`을 실행하여 `RagService`의 초기 임베딩 과정이 에러 없이 "임베딩 완료!" 로그까지 출력되는지 확인합니다.

### Manual Verification
- 임베딩 데이터가 `test.essay_vectors` 테이블에 정상적으로 쌓이는지 쿼리로 확인합니다.
