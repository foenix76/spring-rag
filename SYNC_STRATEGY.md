# 🔄 Master Base Sync Strategy (SYNC_STRATEGY)

This guide explains how to apply and maintain the `base` configuration across multiple projects.

---

## 🆕 1. Starting a New Project
Clone the `base` repository to start with the full AI-ready environment.

```bash
git clone https://github.com/foenix76/base.git <new-project-name>
cd <new-project-name>
git remote remove origin
git remote add origin <new-repo-url>
git push -u origin main
```

---

## 🛠️ 2. Applying to an Existing Project
To integrate these rules into an ongoing project:

1. Copy `.ai/scripts/sync-base.sh` from this repo to the target project.
2. Run the script:
```bash
chmod +x .ai/scripts/sync-base.sh
./.ai/scripts/sync-base.sh
```

**What the script does:**
- Adds `https://github.com/foenix76/base.git` as a remote named `master-base`.
- Fetches and checks out the latest `rules.md`, `MEMORY.md`, and core `.ai/` files.

---

## 🔄 3. Keeping Rules Updated (Intelligent AI Merge) ⭐
When new rules or features are added to the master `base` repo, just tell me:

> **Command**: `/베이스동기화`

**🤖 What the AI Agent does (Intelligent Merge):**
1.  **Fetch**: Fetches the latest changes from the master-base repo.
2.  **Analysis**: Compares the latest master rules with the current project's local rules.
3.  **Preservation**: **Strictly preserves local project-specific context (domain knowledge, custom logic, etc.).**
4.  **Integration**: Intelligently integrates new global rules and commands into the local files.
5.  **Sync**: Updates both `.md` and `_KR.md.bak` to maintain consistency.

---

## 🚀 4. Contributing Back to Base (Intelligent Reverse Merge) ⭐
When you develop a brilliant, generalizable rule, it can be promoted globally using a safe, non-destructive process:

> **Command**: `/베이스기여`

**🤖 What the AI Agent does (Reverse Merge):**
1. **Fetch & Compare**: Fetches the latest master `base` repo and compares it with the local contribution.
2. **De-conflict**: Checks if similar rules were already added to the master by other projects.
3. **Extract & Integrate**: Strips project-specific domain logic and **intelligently integrates (Appends/Merges)** the new rule into the master context.
4. **Verified Push**: **[CRITICAL] Pushes ONLY the semantically merged rules files.** NEVER use `git push` on the whole project branch. Use a temporary directory strategy to ensure no project-specific code (like `wp_pro.py`) is ever pushed to the global `base` repo.

---

## ⚠️ Notes
- **Safety First**: This is an **Intelligent Merge**, not a destructive overwrite. Your project-specific customizations are safe.
- **Commit**: Always review the merged changes and commit the results.
