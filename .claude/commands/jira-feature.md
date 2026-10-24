---
description: Implement a Jira task with OpenSpec, Sonnet 5.5, Figma, task commits and a templated PR.
argument-hint: "<Jira-key-or-URL> [Figma-URL] [base=main]"
---

Run the project workflow in `.agents/skills/jira-feature/SKILL.md` for the following input:

$ARGUMENTS

Read that shared workflow before acting. Use T3 delegation when available; otherwise use the project `jira-feature-implementer` native subagent in an isolated worktree. Keep the parent responsible for the final PR and use `.github/pull_request_template.md`.
