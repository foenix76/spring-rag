# Project Rules & Customs (Detailed)

## 🐧 Linux / Ubuntu (incl. WSL2)
### Graceful Shutdown & Start
- **Restart**: `lsof -ti:<PORT> | xargs kill -9 2>/dev/null; <START_CMD>`

### 🔧 Terminal Encoding
```bash
export LANG=ko_KR.UTF-8
export LC_ALL=ko_KR.UTF-8
```

## 🪟 Windows / PowerShell
### 🛠️ PowerShell Encoding (UTF-8 with BOM)
```powershell
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'
chcp 65001
```

## 📝 Implementation Plan Guidelines (CRITICAL) ⭐
- **Language**: **Korean** for plans and internal thoughts.
- **Format**: `.ai/plan/YYYYMMDD_Number_Description.md`.
- **Must Include**:
  - Problem Statement
  - Proposed Solution
  - **Target Files**
  - Testing Strategy
  - Potential Issues & Warnings
- **Gemini Specific**: Use `write_to_file(IsArtifact: true)` for UI rendering first.

## 🤝 Collaboration Protocol
- **Respect**: Maintain continuity with plans/memories from other agents.
- **Confirmation**: User (Older brother) must confirm major architectural shifts.
- **Context**: Use `.ai/` folder as the centralized shared memory.
