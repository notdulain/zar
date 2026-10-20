<!--
Delete every comment like this one before you open the PR.
Delete any section that does not apply, but keep "What was tested" and "Not tested".
Title format: type(scope): short description (SCRUM-NN)   e.g. feat(transactions): browse Activity and undo entries (SCRUM-36)
-->

## What this does

<!--
One or two sentences, then a short list of what the user can now do.
Write for a reviewer who has not read the ticket. Say what changed for the user, not which files moved.
-->

Implements **SCRUM-NN (SXX): Ticket title**.

-

## How it maps to the acceptance checks

<!--
One row per acceptance check on the Jira ticket, copied in the ticket's own words.
"How" says where to see it: a screen, a test class, a screenshot number. Delete this section if the ticket has no acceptance checks.
-->

| Check | How |
|---|---|
|  |  |

## Behaviour changes to look at

<!--
Anything that changes what existing code or data does, not just adds something new.
Say what changed, why, and what protects it (a test, a rule). Write "None" if there is nothing.
-->

None.

## Links

<!--
JIRA
- One line per ticket. Use the full URL so it is clickable:
  Jira: [SCRUM-NN](https://03dulain.atlassian.net/browse/SCRUM-NN)
- If the PR depends on another PR, name it: "Depends on SCRUM-35 (PR #12, merged)."

FIGMA
- Always link the file: [Zar UI](https://www.figma.com/design/bBBJuQz1Cg8N34FcisKBs9/Zar-UI?node-id=1-2)
- To link a specific screen or frame, open it in Figma, right-click it, choose
  "Copy/Paste as" -> "Copy link to selection". The link ends in ?node-id=293-3484.
  In the URL the node id uses a hyphen (293-3484); in text and tables write it with a colon (293:3484).
- If you added new frames, link their section and list each frame in a table (see below).
  Say whether existing frames were left untouched.
-->

Jira: [SCRUM-NN](https://03dulain.atlassian.net/browse/SCRUM-NN)

Figma: [Zar UI](https://www.figma.com/design/bBBJuQz1Cg8N34FcisKBs9/Zar-UI?node-id=1-2)

<!-- Delete this table if there are no new frames. Put two frames side by side as in the columns below. -->

| Frame | Node | Frame | Node |
|---|---|---|---|
| ZXX Name | `000:0000` | ZXX Name | `000:0000` |

## Screenshots

<!--
HOW TO ADD SCREENSHOTS THAT STAY IN THE PR

1. Save the PNGs in the repo under docs/evidence/<story>/ (for example docs/evidence/S02/01-activity-list.png).
   Number them in the order a reader should look at them, and use lowercase names with hyphens.
2. Commit and push them. The image link needs a commit that exists on GitHub.
3. Build each link from the COMMIT HASH, not the branch name, so the picture does not change or
   disappear if the branch moves or is deleted:
     https://github.com/notdulain/zar/blob/<full-commit-hash>/docs/evidence/<story>/<file>.png?raw=true
   Get the hash with:  git rev-parse HEAD   (use the commit that added the images)
   The ?raw=true at the end is required, otherwise GitHub shows a web page, not the picture.
4. Show them in columns with a Markdown table, three per row, with the header row naming each one.
   Use <img ... width="230"> so a phone screenshot is not shown full size. 220 to 230 fits three across.
5. For a caption under each picture (used in PR #12), put the image and the caption in one cell:
     <td align="center"><img src="..." width="220"><br><sub><b>1.</b> What this shows.</sub></td>
   inside a <table><tr>...</tr></table> block instead of a Markdown table.
6. Link the whole folder at the end so a reviewer can see every screenshot:
     [`docs/evidence/<story>/`](https://github.com/notdulain/zar/tree/<full-commit-hash>/docs/evidence/<story>)

Rules: screenshots must come from a build you actually ran. Say which device or emulator.
Show the states that matter, not only the happy path: empty, error, offline, and the result after a change.
Do not include real names, account numbers or messages. Use seeded test data.
-->

Pixel 4 emulator, local Firebase emulators.

| First screen | Second screen | Third screen |
|---|---|---|
| <img src="https://github.com/notdulain/zar/blob/COMMIT_HASH/docs/evidence/SXX/01-name.png?raw=true" width="230"> | <img src="https://github.com/notdulain/zar/blob/COMMIT_HASH/docs/evidence/SXX/02-name.png?raw=true" width="230"> | <img src="https://github.com/notdulain/zar/blob/COMMIT_HASH/docs/evidence/SXX/03-name.png?raw=true" width="230"> |

All screenshots are in [`docs/evidence/SXX/`](https://github.com/notdulain/zar/tree/COMMIT_HASH/docs/evidence/SXX).

## What was tested

<!--
Only what you ran, with the numbers from that run. Do not write "should work".
- Unit tests: command and result, for example "317 pass, 0 fail (84 new)".
- Android lint, if you ran it.
- Compose UI tests on the emulator: which classes and how many passed.
- By hand: what you did on the emulator and what you saw, one line each. Include offline and reconnect
  if the change writes data, and say how you checked the stored data.
-->

- **Unit tests:**
- **Compose UI tests on the emulator:**
- **By hand:**

## Not tested

<!-- Be plain about the gaps, for example: physical device, dark mode, large fonts, TalkBack, the live Firebase project. -->

-

## Things worth a reviewer's attention

<!--
Anything a reviewer could miss or might disagree with: shared components you touched, things made
less private, test fixtures that changed, shortcuts you took. Say how to undo each one if they object.
-->

-

## Not in this PR (and why)

<!-- Work you left out on purpose, with the reason or the ticket that covers it. -->

-

## Commits

<!-- Optional on a small PR. List them in order, one line each, using the commit subjects. -->

1.
