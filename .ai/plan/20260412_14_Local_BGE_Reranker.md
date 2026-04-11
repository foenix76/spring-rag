# 🚀 구현 계획서: Local BGE-Reranker-v2-m3 (ONNX) 기반 2단계 RAG 도입 (v1.0)

## 1. 개요 (Overview)
현재의 단일 벡터 검색(Bi-Encoder) 방식의 한계를 극복하기 위해, **BGE-Reranker-v2-m3** (Cross-Encoder) 모델을 로컬 ONNX 환경에 도입합니다. 
1단계에서 유사도 기반으로 넓게 후보군(Top N)을 찾고, 2단계에서 질문과의 논리적 연관성을 정밀하게 재평가(Rerank)하여 최종(Top K) 컨텍스트를 선별합니다.

---

## 2. 시스템 아키텍처 및 설정 (Configuration)

### 2.1 모델 디렉토리 구조
- 기존: `models/multilingual-e5-base/` (임베딩용)
- **신규**: `models/bge-reranker-v2-m3/` 폴더 생성 후 `model.onnx` 및 `tokenizer.json` 배치.

### 2.2 `application.yml` 설정 추가
기존 RAG 설정에 Rerank 관련 설정을 추가합니다.
```yaml
app:
  # 기존 임베딩 유지
  reranker:
    model-path: models/bge-reranker-v2-m3/model.onnx
    tokenizer-path: models/bge-reranker-v2-m3/tokenizer.json
    max-token-length: 512
  rag:
    retrieval-top-k: 20   # 1단계 벡터 검색 개수 (기존 top-k를 확장)
    rerank-top-k: 5       # 2단계 최종 선택 개수
    similarity-threshold: 0.3 # 1단계 임계값을 낮춰 더 많은 후보군 허용
```

---

## 3. 핵심 컴포넌트 구현 (Core Implementation)

### 3.1 `OnnxRerankConfig.java` 신설
- 기존 `OnnxEmbeddingConfig`와 분리하여 독립적인 `OrtSession` 및 `HuggingFaceTokenizer` (Reranker용) 빈(Bean)을 생성합니다.
- `OrtEnvironment`는 애플리케이션 전체에서 공유(싱글톤)하여 메모리를 최적화합니다.

### 3.2 `OnnxRerankService.java` 신설
- **Cross-Encoder 추론**: 질문(Query)과 후보 문서(Passage) 쌍을 하나의 입력으로 토큰화(`tokenizer.encode(query, passage)`)합니다.
- **ONNX 추론**: 모델에 입력하여 반환되는 로짓(Logit) 점수를 추출합니다.
- **스마트 정렬 로직**: 후보 문서들의 점수를 계산하고, 내림차순으로 정렬한 뒤 최종 K개를 반환하는 `rerank(query, documents, topK)` 메서드를 제공합니다.

### 3.3 `RagService.java` 연동 로직 고도화
- **기존 흐름 변경**: 
  1. `vectorRepository.searchSimilar` 호출 시 `retrieval-top-k` (예: 20) 적용.
  2. `OnnxRerankService`를 호출하여 20개의 문서를 재평가.
  3. 가장 점수가 높은 5개(`rerank-top-k`)의 문서만 선별하여 `context` 문자열로 결합.
  4. 이후 Spring AI `ChatClient`에 컨텍스트 전달.

---

## 4. 의존성 및 제약 사항 (Dependencies & Constraints)
- **추가 의존성 없음**: 이미 `onnxruntime`과 `djl-tokenizers`가 설정되어 있으므로 기존 인프라를 100% 재활용합니다.
- **성능 관리**: Reranker는 무거운 연산이므로 1단계 후보군을 20~30개 내외로 제한하여 지연 시간(Latency)을 최소화합니다.

---

## 5. 검증 및 테스트 계획 (Validation)
1. **모델 로딩 검증**: 서버 구동 시 `models/bge-reranker-v2-m3/` 모델이 정상 로드되고 `OrtSession`이 생성되는지 확인.
2. **성능/순위 비교**: 
   - 질문: "테슬라에 대해 언급한 지원자는?"
   - 벡터 검색(1단계) 순위와 Reranker(2단계) 통과 후의 최종 순위가 어떻게 변하는지 디버그 로그로 출력하여 확인.
3. **응답 속도 측정**: Rerank 전후의 답변 생성까지 걸리는 지연 시간(Latency) 차이 측정.
