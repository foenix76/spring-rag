---
name: Project Master Memory (SSOT)
description: Unified context for all AI agents (English for Efficiency)
type: project
---

# 🧠 AI Common Memory (SSOT)

This document is the **Single Source of Truth** for all AI agents. It integrates all core knowledge to prevent fragmentation and strictly follows the DRY principle.

---

## 🔑 Key Strategies
- **Dual Language Documentation**: English (`.md`) for AI efficiency, Korean (`_KR.md.bak`) for User records.
- **No-Omission Protocol**: Always provide **100% Full Code**. No summaries or snippets.

## 💾 Documentation Rules (Memory & Plan)
All AI-related files must stay within the `.ai/` directory.

### 1. 🧠 Memory (`.ai/memory/`)
- Append new knowledge to this `MEMORY.md`. Do NOT split into multiple files.

### 2. 📋 Plan (`.ai/plan/`)
- **Format**: `YYYYMMDD_Number_Korean_Description.md`.
- **Must Include**: Problem, Solution, **Target Files**, Testing, Checklist.
- Gemini agents MUST use the `write_to_file` artifact exception.

---

## 📂 Folder Map & Key Files
- `.ai/core/rules.md`: **Global Custom Commands & Tone Guide (Mandatory)**.
- `.ai/core/NEXT_CONTEXT.md`: Current progress & To-Do (Update every session).
- `.ai/memory/MEMORY.md`: This SSOT document.

---

## 🚨 CRITICAL ANTI-PATTERNS
- **NO Redundant Waits**: Do not add extra `sleep` to logic already handling rate limits or concurrency. 🛑
- **NO Omissions**: Never use `// ... existing code`. Always provide the **Full Code**.

---

## 🎯 Current Status
- [2026-04-04] Initial setup with Dual Language strategy completed.
- [2026-04-09] SiliconFlow (Qwen 2.5) RAG system integration completed.
  - Successfully resolved ONNX embedding model input error (input_ids + attention_mask).
  - Populated 100 sample applicants (400 items) into vector DB.
  - Optimized Git repo size (1.5GB -> 1MB) by pruning temporary garbage files.
  - Fixed frontend Vue 3 'this' context issues and server token authentication.
