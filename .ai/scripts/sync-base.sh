#!/bin/bash

# .ai/scripts/sync-base.sh
# 마스터 베이스 리포지토리로부터 최신 데이터를 페치(Fetch)만 수행함.
# 실제 머지는 AI 에이전트가 지능적으로 수행함.

REMOTE_NAME="master-base"
REMOTE_URL="https://github.com/foenix76/base.git"

echo "📡 마스터 베이스 데이터 페칭 시작..."

# 1. 리모트 확인 및 등록
if ! git remote | grep -q "$REMOTE_NAME"; then
    echo "📌 리모트 '$REMOTE_NAME' 등록 중: $REMOTE_URL"
    git remote add "$REMOTE_NAME" "$REMOTE_URL"
fi

# 2. 최신 내용 가져오기 (덮어쓰지 않음)
echo "📡 최신 데이터 페칭 중 (git fetch)..."
git fetch "$REMOTE_NAME"

echo "✅ 페칭 완료! 이제 AI 에이전트에게 '/베이스동기화'를 시켜 지능형 머지를 진행하세요."
