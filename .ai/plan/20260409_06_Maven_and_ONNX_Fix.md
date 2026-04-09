# Maven 실행 및 ONNX 모델 입력 오류 해결 계획

형, `firecrawl`로 잽싸게 검색해서 원인을 다 찾아냈어! 

현재 두 가지 문제가 꼬여 있는데, 하나씩 해결해보자.

## Research Findings & Solutions

### 1. Maven 실행 오류 (`CommandLineUtils` 누락)
- **원인**: `spring-boot-maven-plugin`이 실행될 때 필요한 `plexus-utils` 라이브러리가 현재 메이븐 환경에서 제대로 로드되지 않아 발생하는 문제입니다.
- **해결책**: `pom.xml`의 `spring-boot-maven-plugin` 설정 안에 `plexus-utils` 의존성을 직접 명시해줘서 강제로 로드하게 만듭니다.

### 2. ONNX 모델 입력 오류 (`expected [1,2) found 3`)
- **원인**: 우리가 사용하는 `multilingual-e5-base` 모델은 `xlm-roberta` 기반인데, 이 모델은 원래 `token_type_ids`를 쓰지 않아. 게다가 현재 형 프로젝트에 있는 ONNX 파일은 최적화 과정에서 **오직 1개의 입력(`input_ids`)**만 받도록 변택(?) 변환된 것으로 보입니다.
- **해결책**: `OnnxEmbeddingService.java`에서 `input_ids`만 보내도록 코드를 다듬습니다.

## Proposed Changes

### [Project] Maven 설정 수정

#### [MODIFY] [pom.xml](file:///home/foenix/workspace/spring-rag/pom.xml)
- `spring-boot-maven-plugin` 설정에 `plexus-utils` 의존성을 추가합니다.

### [Backend] OnnxEmbeddingService 수정

#### [MODIFY] [OnnxEmbeddingService.java](file:///home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/OnnxEmbeddingService.java)
- 오직 `input_ids`만 모델에 전달하도록 수정합니다. (아까 시도했던 방향이 맞았어!)

---

## Verification Plan

### Automated Tests
- `mvn spring-boot:run`을 실행하여 빌드 에러 없이 서버가 8091 포트로 정상 기동되는지 확인합니다.
- 기동 로그에서 "임베딩 완료!" 문구가 뜨는지 체크합니다.

### Manual Verification
- 브라우저에서 `http://localhost:8091` 접속이 되는지 확인합니다.
