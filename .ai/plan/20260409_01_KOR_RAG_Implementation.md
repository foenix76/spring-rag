# 구현 계획서: 한국어 RAG 채용 에세이 검색 시스템 (PostgreSQL 개발 환경 버전)

이 문서는 `.ai/plan/KOR-RAG-PROJECT.md` 명세서를 바탕으로 하되, 현재 개발 환경인 PostgreSQL(user: stock1234, schema: test)에 맞춰 수정한 구현 계획입니다.

## 1. 목표
- PostgreSQL 18을 소스 DB 및 벡터 DB로 동시에 활용하여 RAG 시스템 프로토타입 완성.
- 로컬 ONNX 임베딩(`multilingual-e5-base`) 및 pgvector(이미 설치됨)를 이용한 검색 구현.
- Vue 3 기반의 단일 페이지 채팅 UI 제공.

## 2. DB 작업 계획
- **DB**: `postgres` / **User**: `stock1234`
- **Schema**: `test` 스키마 사용.
- **Source Table**: `test.REC_APPLI_MAS` (Oracle 대용)
    - 컬럼: `acceptno`, `name`, `hsgessay1`, `hsgessay2`, `hsgessay3`, `hsgessay4`
- **Vector Table**: `test.essay_vectors`
    - 컬럼: `id`, `accept_no`, `name`, `essay_type`, `chunk_index`, `content`, `embedding (test.vector(768))`

## 3. 주요 수정 사항 (명세서 대비)
- **Versions**: LangChain4j를 **1.12.2** 버전으로 사용.
- **application.yml**: Oracle 설정을 제거하고, 모든 DB 연동을 PostgreSQL로 통합.
- **Entity/Repository**: `ApplicantEssay` 엔티티가 `test.REC_APPLI_MAS` 테이블을 바라보도록 수정.

## 4. 작업 순서
1. **DB 초기화**: `test` 스키마 내 테이블 생성 및 테스트 데이터 삽입.
2. **ONNX 모델 준비**: Python 스크립트를 실행하여 `model.onnx`와 `tokenizer.json` 추출.
3. **백엔드 구현**:
    - `pom.xml` 설정 (Spring Boot 3.0.5, LangChain4j 1.12.2 등)
    - 엔티티, DTO, Repository 작성
    - ONNX 임베딩 및 문서 청킹 서비스 작성
    - RAG 서비스 및 컨트롤러 작성
4. **프론트엔드 구현**: `src/main/resources/static/index.html` (Vue 3 CDN)
5. **검증**: 애플리케이션 실행 후 "테슬라" 키워드로 RAG 검색 테스트.

## 5. 일정 (예상)
- [ ] 1. DB 및 프로젝트 기본 세팅 (10분)
- [ ] 2. ONNX 모델 추출 및 배치 (10분)
- [ ] 3. 백엔드 핵심 로직 구현 (30분)
- [ ] 4. UI 및 최종 테스트 (10분)
