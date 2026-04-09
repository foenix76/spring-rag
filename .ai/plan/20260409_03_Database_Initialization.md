# 데이터베이스 초기화 및 기동 실패 해결 계획

애플리케이션 기동 시 `test.rec_appli_mas` 테이블이 없어 발생한 `PSQLException`을 해결하기 위해 필요한 데이터베이스 스키마와 테이블을 생성합니다.

## User Review Required

> [!IMPORTANT]
> 현재 `test` 스키마에 테이블이 전혀 없습니다. 에세이 데이터를 어디서 가져와서 넣어야 할지 확인이 필요합니다. 우선 애플리케이션이 정상 기동될 수 있도록 빈 테이블이라도 생성하는 방향으로 진행하겠습니다.

## Proposed Changes

### [Database] 스키마 초기화

#### [NEW] [schema.sql](file:///home/foenix/workspace/spring-rag/src/main/resources/schema.sql)
- `test` 스키마 생성 (혹은 확인)
- `REC_APPLI_MAS` 테이블 생성 (지원자 에세이 원본 데이터용)
- `essay_vectors` 테이블 생성 (임베딩 벡터 저장용, pgvector 활용)

#### [MODIFY] [application.yml](file:///home/foenix/workspace/spring-rag/src/main/resources/application.yml)
- 형이 수정한 포트 `8091` 설정을 유지합니다.
- `spring.jpa.hibernate.ddl-auto` 설정을 `none`으로 유지하고, 대신 `schema.sql`을 통해 명시적으로 관리하는 것을 권장합니다.

---

## DDL 명세 (Pre-view)

```sql
CREATE SCHEMA IF NOT EXISTS test;

-- 지원자 에세이 테이블
CREATE TABLE IF NOT EXISTS test.rec_appli_mas (
    acceptno VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100),
    hsgessay1 TEXT,
    hsgessay2 TEXT,
    hsgessay3 TEXT,
    hsgessay4 TEXT
);

-- 벡터 저장소 테이블
CREATE TABLE IF NOT EXISTS test.essay_vectors (
    id SERIAL PRIMARY KEY,
    accept_no VARCHAR(50),
    name VARCHAR(100),
    essay_type VARCHAR(20),
    chunk_index INTEGER,
    content TEXT,
    embedding public.vector(768), -- multilingual-e5-base는 768차원
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (accept_no, essay_type, chunk_index)
);
```

---

## Verification Plan

### Automated Tests
- DDL 적용 후 `mvn spring-boot:run`을 실행하여 `test.rec_appli_mas` 조회 에러 없이 기동되는지 확인합니다.
- 포트 `8091`로 서비스가 정상 노출되는지 확인합니다.

### Manual Verification
- `test.rec_appli_mas`에 샘플 데이터를 한 건이라도 넣고 임베딩 프로세스가 도는지 확인합니다.
