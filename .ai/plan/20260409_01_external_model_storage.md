# [구현 계획] RAG 모델 외부 저장소(External Storage) 리팩토링

현재 1.1GB가 넘는 대형 모델 파일을 JAR 파일 내부에 패키징할 때 발생하는 데이터 유실 이슈를 해결하기 위해, 모델 파일을 외부 폴더에서 직접 로드하는 방식으로 전환합니다.

## User Review Required

> [!IMPORTANT]
> **운영 방식 변경**: 이제 JAR 파일 하나만으로는 실행되지 않으며, 반드시 `models/` 폴더가 JAR 파일과 같은 위치에 있어야 합니다. 이는 대용량 AI 모델을 다루는 표준적인 방식이며 안정성이 훨씬 높습니다.

## Proposed Changes

### [Backend] AI 모델 로드 로직 수정

#### [MODIFY] [OnnxEmbeddingConfig.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/config/OnnxEmbeddingConfig.java)
- `ClassPathResource`를 사용하여 JAR 내부에서 읽던 로직을 `java.nio.file.Path`를 사용하여 외부 파일 시스템에서 직접 읽는 방식으로 변경합니다.
- 파일이 없을 경우 친절한 에러 메시지를 출력하도록 예외 처리를 강화합니다.

#### [MODIFY] [application.yml](file:///home/foenix/workspace/spring-rag/src/main/resources/application.yml)
- `model-path` 및 `tokenizer-path`를 classpath 기준이 아닌 상대 경로(Relative Path) 기준으로 변경합니다. (예: `models/multilingual-e5-base/model.onnx`)

### [Documentation] 이사 가이드 업데이트

#### [MODIFY] [프로젝트해설.md](file:///home/foenix/workspace/spring-rag/프로젝트해설.md)
- "JAR 하나면 된다"는 내용을 "JAR + 모델 폴더" 조합으로 수정하고, 왜 이 방식이 더 안정적인지 보완 설명합니다.

## Verification Plan

### Automated Tests
1. `mvn clean package` 수행 후 `target/kor-rag-1.0.0.jar` 생성을 확인합니다.
2. `target/` 폴더로 JAR 파일을 이동시킨 후, 별도의 `models/` 폴더를 준비하여 `java -jar`로 기동 테스트를 수행합니다.
3. ONNX 모델 로딩 로그 및 실제 RAG 작동 여부를 확인합니다.

### Manual Verification
- 유저가 직접 윈도우 환경으로 JAR와 모델 폴더를 복사한 후 정상 작동하는지 테스트가 필요합니다.
