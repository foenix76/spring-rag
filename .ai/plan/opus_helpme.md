# Opus 형님, 도와주세요! (spring-rag 프로젝트 긴급 지원 요청)

## 1. 프로젝트 개요
- **목표**: 한국어 채용 에세이 RAG 검색 시스템 구현.
- **특이사항**: 로컬 ONNX(`multilingual-e5-base`) 임베딩 사용, PostgreSQL 18 pgvector 검색.
- **진행 상황**: 전체 코드(Spring Boot + Vue 3)는 작성되었으나, Maven 빌드 환경 이슈와 LangChain4j 1.12.2 버전의 API 변화로 인해 컴파일 에러 발생 중.

## 2. 기술 스택
- **Java 17** / **Spring Boot 3.0.5**
- **LangChain4j 1.12.2** (최근 업데이트된 버전으로 API 확인 필요)
- **Lombok 1.18.36** (JDK 17 호환을 위해 지정)
- **PostgreSQL 18** (User: `stock1234`, Password: `stock1234`, Schema: `test`)
- **Frontend**: Vue 3 CDN (Options API 스타일의 Composition API)

## 3. 주요 해결 과제 (Blockers)

### A. LangChain4j 1.12.2 API 불일치
`src/main/java/com/example/korrag/service/RagService.java`에서 다음 에러 발생:
1. `OpenAiChatModel.withApiKey(apiKey)` -> 심볼을 찾을 수 없음. (최신 버전에서는 Builder 패턴 사용 필요성 검토)
2. `chatModel.generate(prompt)` -> 심볼을 찾을 수 없음. (최신 인터페이스 확인 필요)

### B. Maven 빌드 환경 정리 완료
- **Kakao 미러 이슈**: `~/.m2/settings.xml`에서 쓰레기 같은 카카오 미러를 영구 추방함. 이제 중앙 저장소에서 직접 받음.
- **Maven 캐시**: 깨진 POM 파일들이 로컬에 남아있을 수 있으니 `mvn clean compile -U` 권장.

## 4. DB 정보 (이미 형이 세팅 완료)
- **Source Table**: `test.REC_APPLI_MAS` (acceptno, name, hsgessay1~4)
- **Vector Table**: `test.essay_vectors` (embedding 컬럼 타입: `test.vector(768)`)

## 5. 현재 파일 구조
- `pom.xml`: LangChain4j 1.12.2, Lombok 1.18.36 설정 완료.
- `src/main/java/com/example/korrag/...`: 백엔드 소스 위치.
- `src/main/resources/application.yml`: DB 및 OpenAI 키 설정.
- `src/main/resources/static/index.html`: Vue UI.
- `.gitignore`: 1.1GB 모델 파일(`model.onnx`) 푸시 방지 설정 완료.

## 6. 오퍼스 형님께 부탁드리는 작업
1. `RagService.java`의 LangChain4j 코드를 1.12.2 버전에 맞게 수정해주세요.
2. `mvn compile`을 돌려 빌드가 완벽히 통과하는지 확인해주세요.
3. 가능하다면 `mvn spring-boot:run`까지 확인해주시면 최고입니다.

**동생(Gemini)이 삽질한 거 깔끔하게 마무리 부탁드립니다!**

---

## 7. 수정 완료 내역 (2026-04-09)

### 수정 파일: `src/main/java/com/example/korrag/service/RagService.java`

| 위치 | 기존 코드 | 수정 코드 |
|------|-----------|-----------|
| 생성자 (38번 라인) | `OpenAiChatModel.withApiKey(apiKey)` | `OpenAiChatModel.builder().apiKey(apiKey).build()` |
| `ask()` 메서드 (71번 라인) | `chatModel.generate(prompt)` | `chatModel.chat(prompt)` |

**원인**: LangChain4j 1.x에서 `OpenAiChatModel`이 `ChatLanguageModel` 인터페이스 대신 `ChatModel` 인터페이스를 구현하도록 변경됨. `withApiKey()` 정적 팩토리 메서드 제거 → builder 패턴으로 대체, `generate(String)` → `chat(String)` 으로 변경.

**결과**: `mvn compile` → **BUILD SUCCESS** 확인.
