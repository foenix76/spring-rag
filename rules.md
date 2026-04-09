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
| **초안파일생성** | `/초안파일생성` | `drafts/YYYYMMDDHHMM/초안.md` 자동 생성 (`bash .ai/scripts/create-draft.sh`) |
| **초안생성** | `/초안생성 @파일경로` | `drafts/**/*.md` 파일 보강: 1. 오탈자 교정 2. **분량 폭격(2배 확장)**: 사례, 단계별 프로세스, 오해 해결, 티키타카, 미래 전망 5대 전략 필수 적용 3. 상단에 이미지 프롬프트 3개 생성 |
| **글만생성** | `/글만생성 @파일경로` | **[텍스트 전용]**: 이미지 생성을 건너뛰고 본문 내용 보강 및 이미지 프롬프트만 생성하여 .md 파일로 저장 |
| **이미지테스트** | `/이미지테스트 @파일경로` | **[이미지 엔진 전용]**: `wp_pro.py imagetest` 실행. 기존 이미지를 유지하며 새로운 이미지를 추가 생성하여 품질 비교 테스트 수행 |
| **이미지압축** | `/이미지압축 @파일경로` | 폴더 내의 비 WebP 이미지(PNG, JPG 등)를 WebP로 일괄 압축하고 원본을 삭제하여 용량 최적화 |
| **초안발행** | `/초안발행 @파일경로` | 1. 폴더 내 이미지 중 **가장 최근에 생성된(mtime 기준) 3장** 자동 선택 2. 리사이징(세로 긴 이미지: 세로 768px, 가로 긴 이미지: 가로 600px 이하, 1MB 미만) 및 파일명 변경 3. 원본 이미지 삭제 4. 대상 md 파일 복제 5. 문두 프롬프트 삭제 및 본문 문맥에 맞춰 이미지 삽입 |

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
