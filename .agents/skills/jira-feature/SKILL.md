---
name: jira-feature
description: Take a Zar Jira task through OpenSpec planning, Figma-guided Sonnet 5.5 implementation in a worktree, task-by-task commits, and a pull request using the project template.
---

# Jira feature to pull request

Accept a Jira key or URL, optionally a Figma selection URL and `base=<branch>`.
Examples: `$jira-feature SCRUM-42` or `/jira-feature SCRUM-42 <Figma-URL> base=main`.
If the task is missing, ask for its key or URL before starting feature work.

Invocation requests the complete workflow, including local branches, task commits, pushing the feature branch and opening a PR. Respect narrower instructions and runtime permissions. Do not merge the PR, deploy, change billing or message teammates as part of this workflow.

## 1. Fetch the task and design

- Read applicable `AGENTS.md`/`CLAUDE.md`, `README.md`, `docs/domain-contracts.md`, `docs/design-system.md` and `.github/pull_request_template.md`.
- Fetch the issue from connected Jira, including its description, acceptance checks, relevant comments, attachments and dependencies. Resolve the Atlassian site using the connector's discovery rules. Use the actual issue, not the URL title or assumptions from a previous ticket. If Jira is inaccessible, report the access failure instead of inventing a specification.
- For UI work, inspect the exact Figma frames linked by the user or ticket, including relevant error, empty, picker, modal and theme states. If no selection is linked, locate the matching screens in [Zar UI](https://www.figma.com/design/bBBJuQz1Cg8N34FcisKBs9/Zar-UI?node-id=1-2). Load the available Figma implementation skill before design-context calls. Capture node IDs, design context, screenshots and assets for the handoff. Reuse the existing Compose design system and match Figma's layout, typography, colors, icons and interactions. Do not redesign the feature or edit Figma without a request. If the relevant design cannot be identified or read, resolve that blocker before UI implementation. Backend-only tickets do not need invented screens.
- Follow the project's SmartRail consultation instructions for material product or domain decisions the code and design cannot settle. Report unavailability; do not start SmartRail or claim it approved a decision.

## 2. Prepare a branch and OpenSpec

- Inspect Git status, worktrees and existing PRs for the task before creating anything. Reuse a matching active feature or PR rather than duplicating it. Preserve unrelated edits and never stage them into this task.
- Default to the latest `origin/main`, or the explicitly requested base for stacked work. Prepare a dedicated feature branch such as `feature/SCRUM-42-short-name` using the host's supported worktree mechanism. Record the selected base ref and SHA. Do not base an unrelated feature on whichever feature happens to be checked out.
- In T3, use the app's workspace tools when moving the current thread; a handoff ends the turn, so provide a continuation containing the task, design links and remaining workflow. Never create a top-level thread merely to launch the requested subagent. If the current workspace cannot safely use the intended base, report the workspace constraint before delegating.
- Ensure `.gitignore` covers `openspec/`, `spec/` and `specs/`. Never force-add these plans or remove previously tracked files without checking their ownership.
- If OpenSpec is not initialized, use `openspec init --tools none --no-animation`. Reuse an existing matching change; otherwise create `scrum-42-short-name` with `openspec new change`. Read the CLI's artifact instructions and existing relevant specs.
- Write the proposal, capability specs, design and `tasks.md`. Preserve the ticket's acceptance wording and scope. Include Figma node references and the observable states to verify. Split implementation into small numbered checkbox tasks, each with its validation and documentation/evidence needs, so each can land as its own commit.
- Run `openspec validate <change> --strict` and confirm all planning artifacts are complete. Resolve implementation-changing questions before delegation.
- Commit only tracked setup changes that the child needs, such as new ignore rules. Record the handoff commit SHA. Ignored OpenSpec files and other uncommitted files do not automatically reach a child worktree.

## 3. Delegate implementation to Sonnet 5.5

Read [the worker contract](references/implementation.md) and include it in the child's brief, together with the issue content, acceptance checks, source OpenSpec path or artifact contents, expected base/handoff SHA, feature branch, Figma context, project constraints and validation commands. A child cannot rely on this conversation being copied.

**T3:** Discover `orchestrator_capabilities` and select the enabled Claude provider instance advertising the exact `claude-sonnet-5-5` model. Use `delegate_task` with `isolation: "worktree"`, `role: "implementation"`, `mode: "async"`, the selected provider/model, and advertised options. Do not hard-code a provider instance ID or substitute another model. Retain the task ID and a stable client request ID for retries; check the returned provider, model and options. Model options belong in `target.options`. Follow the host's documented transport fallback if its tools are not directly exposed.

**Native Claude Code without T3:** Invoke the project `jira-feature-implementer` subagent with explicit worktree isolation. Its model is pinned to `claude-sonnet-5-5`; do not override it with an alias. Have the worker verify the handoff SHA before edits, because native worktree defaults can differ from T3's parent-commit behavior.

**Codex without a Claude delegation provider:** Report that Sonnet 5.5 delegation is unavailable. Keep the completed spec, but do not silently implement with Codex or pretend a Sonnet worker ran.

The parent owns orchestration and the PR; the child owns implementation, tests, evidence and task commits. While it runs, the parent can inspect integration points and verify the baseline without editing files the child owns. Keep the user informed. In T3, rely on completion notifications instead of polling; use task status when its result is needed. Do not duplicate active work. Before restarting interrupted work, inspect its state and commits and include that recovery context in any replacement task.

## 4. Integrate and verify

- Review the returned code, acceptance coverage, commit SHAs, test results, screenshots and limitations. An interrupted turn or a checked task without evidence is not completion.
- Integrate the child's commits in order into the feature branch without squashing the task history. Copy back the ignored OpenSpec task progress. Reconcile shared-file changes without overwriting another agent's work.
- Fix material gaps and run checks appropriate to the final diff. For Android changes, run `./gradlew testDebugUnitTest assembleDebug`; also compile release when build-variant wiring changes. Run backend checks in `functions/` when changed, and the Firestore test suite when rules change. Add targeted UI or integration checks when behavior requires them. Document checks not run and why. Do not use live credentials, deployments or paid provider calls as an implicit part of testing.
- For UI changes, run the app, compare relevant states with the referenced Figma frames, and capture screenshots from the actual build using test data. Save them under `docs/evidence/<story>/`, commit and push them, and embed them in the PR using full commit-SHA GitHub image URLs ending in `?raw=true`. Include changed pickers, modals, validation and other relevant states, not only the initial screen. Update images after subsequent UI edits. Do not substitute Figma exports for running-app evidence. If a build cannot be run, state the blocker and do not claim visual verification or final readiness.
- Verify strict OpenSpec validation, ignored planning directories, a clean scoped diff and a task-to-commit summary. Leave incomplete items unchecked and explain their remaining work. Do not change Jira status or checklists unless requested; when asked, only mark verified completed work and preserve unrelated description content.

## 5. Push and open the PR

- The parent is the sole publisher. Push the feature branch without force-pushing. Reuse an existing PR for the same branch and base; otherwise open one against the recorded base.
- Read `.github/pull_request_template.md` again immediately before writing the body. Populate its actual headings and instructions, remove template comments/placeholders and omit only inapplicable optional sections. Keep **What was tested** and **Not tested**. Do not replace the template with a generic summary/validation body.
- Follow the template's title convention: `type(scope): short description (SCRUM-NN)`. Copy each acceptance check into its table, with concrete implementation/test/evidence references and partial status where needed. Include Jira and the Zar UI file, exact frames where relevant, behavior changes, screenshots, measured test results, reviewer concerns and deliberate exclusions.
- With `gh`, write the full Markdown body to a temporary file and pass `--body-file`. Read the published title/body back to verify the template, links and evidence. Do not publish raw notes, credentials or private test records.
- In T3, call `link_pull_request` with the full URL immediately after creation or when starting work on an existing PR. Before finishing, check `list_thread_pull_requests` and register any missing PR from this work.
- Return the PR URL, feature branch, OpenSpec location, task/commit summary, tests and remaining setup or verification gaps. Do not claim deployment, device testing or visual parity that was not performed.
