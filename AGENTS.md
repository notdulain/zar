# Pull requests

For every pull request that changes a user interface, add screenshots of the changed screen or state directly to the pull request description. Capture them from the running app after the change, include the relevant states (for example, an open modal and its picker), and use image links that reviewers can open without local files. Update the description whenever later UI edits make those screenshots stale.

## Reusable review command

Use Claude `/pr-review <PR-number-or-URL>` or Codex `$pr-review <PR-number-or-URL>` for the project review-and-adjustment workflow in [.agents/skills/pr-review/SKILL.md](.agents/skills/pr-review/SKILL.md). It covers Figma checks, fixes on the same PR branch, verification, fresh screenshot evidence, every file's Viewed box, a plain-English review, and an authorized merge. Arguments such as `review only` or `do not merge` narrow the scope.
