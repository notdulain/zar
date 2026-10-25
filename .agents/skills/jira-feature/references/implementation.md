# Sonnet 5.5 implementation worker contract

Use this contract in the delegated task prompt. The parent supplies the Jira requirements, OpenSpec artifacts, design context, base/handoff SHA, scope and feature branch; request missing essentials before changing code.

## Start in the assigned worktree

- Confirm the worktree, current branch, clean state and expected handoff commit. Work only in the isolated checkout. Do not alter another checkout or another agent's branch.
- T3 children start from the parent's committed state; native Claude worktrees can start from the default branch. If a newly created, clean child checkout has the wrong base, create its implementation branch at the parent's supplied handoff SHA before editing. Do not reset or overwrite existing work.
- Copy the ignored OpenSpec config and matching change from the absolute path the parent supplied, or reconstruct them from the supplied complete artifacts. Read proposal, design, specs and tasks before implementation. Do not assume ignored/uncommitted plans arrived with the checkout and never force-add them.
- Read applicable project instructions and the task's relevant domain/design contracts. Obtain referenced Figma context using the available connector and required Figma skills if it was not included in the brief. If access is unavailable, return the design blocker before guessing at UI.

## Execute task by task

- Implement the approved spec with `claude-sonnet-5-5`. Stay within ticket scope and match the referenced Figma states using existing Compose tokens and components. Keep domain code independent of Android/Firebase and connect data through Hilt.
- Finish one numbered implementation task at a time. Run its meaningful validation, include its tests and documentation/evidence, inspect the diff, and stage only that task's files. Commit that completed task before starting the next; do not accumulate the feature into one final checkpoint commit.
- Use the repository's commit conventions. Include the Jira key and OpenSpec task ID in the subject or body so each completed task maps to a commit. For example, `feat(accounts): validate account names (SCRUM-42)` with `OpenSpec task 2.1` in the body. Do not invent test counts or mark a task complete after a failing check.
- Mark the local ignored task checkbox complete after its validation and commit succeed. Record any necessary dependency-driven task regrouping with its reason rather than silently dropping task boundaries.
- For UI tasks, run the app and capture the relevant Figma-matched states using test data. Commit screenshots under `docs/evidence/<story>/`; report the capture commit, filenames and device/emulator. Refresh them after later UI changes. State runtime blockers rather than fabricating screenshots.
- Protect secrets and ignored machine configuration. Any reused local build configuration must remain ignored and its contents must not be printed. Use local emulators/test doubles unless the user explicitly authorized live service tests. Do not deploy.

## Return control to the parent

- Finish the relevant feature/integration checks and document actual results and limitations. Return the worktree path, branch, ordered task-to-commit mapping, head SHA, tests and counts, evidence paths, OpenSpec task-file location and any incomplete acceptance checks.
- Do not push, open a PR, update Jira, message teammates, merge, or monitor a PR. The parent handles publishing and any separately requested Jira updates.
- If interrupted, leave a recoverable status describing completed commits, uncommitted files, running checks and the next task. Never describe partial implementation as complete.
