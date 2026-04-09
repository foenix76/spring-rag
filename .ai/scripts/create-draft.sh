#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DRAFTS_DIR="$ROOT_DIR/drafts"
TS="$(date +%Y%m%d%H%M)"
TARGET_DIR="$DRAFTS_DIR/$TS"

# 같은 분에 재실행될 경우 충돌을 피하기 위해 초 단위를 붙여 백오프한다.
if [[ -d "$TARGET_DIR" ]]; then
  TS="$(date +%Y%m%d%H%M%S)"
  TARGET_DIR="$DRAFTS_DIR/$TS"
fi

TARGET_FILE="$TARGET_DIR/초안.md"
mkdir -p "$TARGET_DIR"

cat > "$TARGET_FILE" <<EOF
# 제목을 입력하세요

## 개요
- 주제:
- 대상 독자:
- 핵심 메시지:

## 본문

## 마무리
EOF

echo "✅ 생성 완료: $TARGET_FILE"
