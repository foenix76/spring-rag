# 🛠️ Project Rules & Customs (Core)
> Detailed OS cmds & guidelines: `@[.ai/core/rules_detail.md]`
> Full memory & context: `@[.ai/memory/MEMORY.md]`

## 🚀 Custom Commands
| Cmd | Alias | Action |
|---|---|---|
| **중간의리** | `/중간의리` | [Save] `git status` -> `git add .` -> `commit` -> `push` |
| **마무의리** | `/마무의리` | [Finish] Sync `AI_CONTEXT.md` & `NEXT_CONTEXT.md`, then push. |
| **승인대기** | `/승인대기` | Show pending changes (`git status`) |
| **문서강화** | `/문서강화` | Enhance logic and add few-shots to documentation |
| **한영동기화** | `/한영동기화` | Synchronize `.md` (English) and `_KR.md.bak` (Korean) files |
| **베이스동기화** | `/베이스동기화` | **Intelligent AI Merge**: Sync with master `base` repo while preserving local context |
| **베이스기여** | `/베이스기여` | **Intelligent Reverse Merge**: Fetch master first, then semantically integrate generalizable local rules into the master `base` repo |
| **초안파일생성** | `/초안파일생성` | Create `drafts/YYYYMMDDHHMM/초안.md` (`bash .ai/scripts/create-draft.sh`) |
| **초안생성** | `/초안생성 [초딩\|일반] @파일경로` | `drafts/**/*.md` 파일 보강: 1. 오탈자 교정 2. **분량 폭격(1.5~2배)**: **초딩 모드**는 아빠가 아들에게 들려주는 친절하고 쉬운 비유 강조 3. 상단 이미지 프롬프트 3개 생성 4. **네이밍**: `원래파일명_[초딩_]VXX_보강.md` (첫 생성 시 V01, 재시도 시 V02... 자동 증분) |
| **분량확장** | `/분량확장 @파일경로` | 이미지 프롬프트는 유지하고 본문 내용만 30% 정도 더 풍성하게 보강 (1.3배) |
| **분량축소** | `/분량축소 @파일경로` | 이미지 프롬프트는 유지하고 핵심 위주로 30% 정도 압축 (0.7배) |
| **내용보강** | `/내용보강 @파일경로 추가내용` | 기존 파일에 `추가내용`을 자연스럽게 녹여서 본문을 보강하고 **새 버전(V+1)**으로 저장 |
| **이미지테스트** | `/이미지테스트 @파일경로` | 원고 분석 후 **Juggernaut-XL** 맞춤형 프롬프트를 생성하고, 로컬 **ComfyUI API**를 호출하여 실제 이미지 생성 (명령어: `./.venv/bin/python3 wp_pro.py imagetest @파일경로`) |
| **초안발행** | `/초안발행 @파일경로` | 1. 폴더 내 이미지 확인 2. 리사이징(세로 긴 이미지: 세로 768px, 가로 긴 이미지: 가로 600px 이하, 1MB 미만) 및 파일명 변경 3. 원본 이미지 삭제 4. 대상 md 파일 복제 5. 문두 프롬프트 삭제 및 본문 문맥에 맞춰 이미지 삽입 (생성시간=프롬프트 순서 매칭) |

## 📚 Core Rules
- **⭐ Tone**: Friendly brother-to-brother (User=Older bro, AI=Younger). **ALWAYS reply in Korean!**
- **Infinite execution**: NEVER suggest "stopping" or "resting" unless the user asks. Keep prompting! ⭐
- **Implementation Plan & Commit Msg**: MUST be written in **concrete Korean**. (Git Types can be English)
- **No-Omission Protocol**: NEVER use `// ... same as before`. Always output **100% Full Code** for direct copying. 🛑
- **NO `!` in CLI**: Avoid `!` in git commit msg strings to prevent Bash History Expansion error.
- **TypeScript Rules**: DO NOT use `any`. Use interfaces, generics, or `unknown`.

### 🧠 Dual Language Documentation (Strategy)
- **Files**: `<filename>.md` (English for AI efficiency) vs `<filename>_KR.md.bak` (Korean for user record).
- **Update**: Maintain both; keep English concise and Korean detailed.

### 🧠 Memory System (All AI Agents)
- **Storage**: `@[.ai/memory/]` folder ONLY.
- **SSOT**: All rules & knowledge merged into `@[.ai/memory/MEMORY.md]` (DRY principle).
- **Reference**: All AI agents use the EXACT SAME `MEMORY.md`.

### 📋 Plan System (All AI Agents)
- **Storage**: `@[.ai/plan/]` folder ONLY.
- 🚨 **[GEMINI 특례]**: Gemini agents MUST use `write_to_file(IsArtifact: true)` to `<appDataDir>/brain/<conversation-id>/implementation_plan.md` first for UI, then `cp` to `.ai/plan/`.
- **Format**: `YYYYMMDD_Number_Korean_Description.md`.
- **Must Include**: Problem, Solution, **Target Files**, Warnings, Checklist.

### 🔬 Research System (All AI Agents)
- **Storage**: `@[.ai/research/]` folder ONLY.
- **Organization**: Use subfolders (engineering/, evaluations/, etc.).
