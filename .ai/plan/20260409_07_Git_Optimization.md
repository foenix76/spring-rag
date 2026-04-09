# Git 리포지토리 용량 최적화 및 클리닝 계획

.git 디렉토리의 비정상적인 용량(1.5GB) 원인이 커밋된 파일이 아닌 임시 쓰레기 파일(tmp_pack)임이 확인되었습니다. 이를 안전하게 제거하고 향후 재발을 방지하는 계획입니다.

## User Review Required

> [!NOTE]
> 본 작업은 Git 내부의 임시 파일만 제거하며, 프로젝트의 실제 소스 코드나 `models/` 폴더의 모델 파일에는 영향을 주지 않습니다.

## Proposed Changes

### [Git Optimization]

#### [CLEANUP] `.git/objects/pack/tmp_pack_*`
- `git prune --expire now`: 연결되지 않은 임시 오브젝트들을 즉시 제거합니다.
- `git gc --prune=now --aggressive`: 저장소 전체를 재압축하고 불필요한 데이터를 완전히 삭제합니다.

#### [MODIFY] [.gitignore](file:///home/foenix/workspace/spring-rag/.gitignore)
- 대용량 바이너리 모델 파일(`.onnx`)이 실수로라도 포함되지 않도록 제외 규칙을 강화합니다.
- `venv/` 디렉토리와 빌드 결과물(`target/`)이 확실히 제외되도록 다시 한번 점검합니다.

## Open Questions
- 없음 (이미 모든 reachable object를 전수 조사하여 대용량 커밋이 없음을 확인했습니다.)

## Verification Plan

### Automated Tests
- `du -sh .git`: 작업 후 .git 디렉토리의 용량이 크게 줄어들었는지 확인 (수 MB 수준 예상)
- `git count-objects -vH`: 'garbage' 항목이 0이 되었는지 확인

### Manual Verification
- `mvn compile`: 최적화 후에도 프로젝트 빌드 및 실행에 문제가 없는지 확인 (모델 파일 보존 여부 체크)
