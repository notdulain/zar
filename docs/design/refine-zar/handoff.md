# Task 1 — design contract and audit

Owner requested GPT execute this task directly and check task 5. This replaces the earlier model assignments. Originals remain on Screens; revisions are isolated on [the GPT handoff page](https://www.figma.com/design/bBBJuQz1Cg8N34FcisKBs9/Zar-UI?node-id=768-22790).

## Provenance and foundations

Read live page/variable/text/effect inventories; inspected Light/Dark Plans, Home, Spending, session and registration sources. Source nodes are recorded in OpenSpec design.md; launcher source is unchanged SVG `app/src/main/assets/brand/zar-mark.svg` from `281:3520`. Revised source copies occupy `768:22797`–`768:25458`. New state and motion frames occupy `770:1095`–`770:7339`. No source frame or original style was overwritten.

New `Refine/Label`, `Caption`, `Body`, `Title` styles use Geist. Mono labels in revision copies use the sans label style. `Refine/Card flat` removes cast shadow; `Refine/Floating nav depth` remains separate at y4 / blur8 / alpha12%. Native system status/keyboard typography remains platform-owned.

## Plans chrome and states

Revised Light/Dark main: `768:22797`, `768:22934`. Four fixed slots: (width − 48)/4, giving 91dp at 412 and 78dp at 360. Navbar height68 at y811 on 915dp frame. New plan is a separate 124×48 action at y751; 12dp gap above navbar. Content ends at y727: 24dp above action, total bottom clearance188. Use measured inset + action height + gap + nav height + content gap at runtime. Keep this geometry across all tabs; other tabs retain transaction action. Large text: action174×56, single-column content, wrapping text; clearance196. Compact/large frames are design projections, not device acceptance.

Secondary goal, all goals, create/edit, initial load, load/save failure exist in both modes. Main selection is explicit; creating another goal does not change it. Error states retain draft and existing goals; initial load failure cannot become empty/no-goal. Goal identity is stable per creation attempt. The main frame's content viewport scrolls above chrome, with separate compact and 150% single-column studies.

## Home and Spending

Bars show every date and cumulative observed income; example [100,0,50] → [100,100,150]. Future slots remain muted markers. Tooltip anchors beside the selected bar, clamps inside chart bounds, fades180ms and retargets the shared critically damped spring. Home month control is top right like Activity; eye and cropped plan disclaimer are removed by owner instruction. The donut uses Not tracked for plans because allocations lack dated history. No lifetime allocation is attributed to the selected month. Still free is a residual of recorded received/spent, never bank balance; coverage-dependent percentages and shares are unavailable when misleading. Overspend stays signed and labelled. Line retains six monthly totals, average/delta, partial current month and unknown-rate coverage.

Loading/empty/unresolved states exist for bars/donut/trend/Z15; donut and Z15 also include overdrawn states. State panels are editable designs; their sample numbers are fixtures, not financial data. Review routes lead to existing filtered Activity/review behavior without writing money.

## Authentication and motion

Z38/Z43/Z44/keyboard copied in both modes. Dusk is invariant; Light CTA white, Dark CTA accent. Motion studies `770:4012`–`770:4187`, `770:7164`–`770:7339` include editable 0–240ms translation12→0 / opacity.88→1 tracks on descendants; reduced variants stay at final geometry/opacity. Shipping Compose uses the shared critically damped spring (damping1, stiffness400), press scale.98, and no input delay. Editing, IME and theme changes never replay entry. Input focus underline and errors retarget; interrupted navigation cancels old effects. Reduced motion has no spatial travel; animator0 resolves immediately. Splash has the owner-requested Material sweep and no minimum display time. No new auth features.

## Source inventory and remaining implementation findings

The accompanying text inventories enumerate source paths/line numbers for all matched Back entry points/windows, elevation paths, Mono and launcher references. Mono source scan is empty. Back entry points converge on Zar route transitions, SheetBackHost/ZarModalSheet and ZarDialogWindowMotion; IME gets first Back, gesture cancellation restores the current screen, RTL still exits physically right, roots defer to Android. Inventory is static coverage, not proof of every runtime path.

Remaining cast-depth matches belong to pickers/sheets, selected controls, floating FAB/chrome and the dedicated nav effect. These require classifying against the flat-card contract; a scan match alone is not a card defect. Plans bottom-action implementation remains task3.5: the existing app clearance104 is smaller than the revised two-row design188/196. No app implementation is silently changed by this design task.

Task5 was checked at owner request; the device-test cleanup incident and unverified data recovery remain documented in `docs/evidence/task5/README.md`. Integrated device acceptance remains task6.

## Request-to-source map

| Request | Implementation source / audit evidence |
|---|---|
| R01 Zar icon | `scripts/brand/generate-launcher.py`, brand SVG, `launcher.txt` |
| R02 rightward Back | `core/navigation/RouteTransitions.kt`, `SheetBack.kt`, `ZarDialogs.kt`, `back-and-windows.txt` |
| R03 Geist | `core/presentation/theme/Type.kt`, `ZarDesign.kt`, empty `mono.txt` source scan |
| R04 bottom New plan | `features/plans/presentation/screen/PlansScreen.kt`, `core/presentation/components/ZarBottomNav.kt`, revised Plans frames; task3.5 pending |
| R05 multiple plans | `features/plans/domain`, goal catalog repository/viewmodels; task3 commits |
| R06 Sign out bounds | `features/settings/presentation/screen/SettingsScreen.kt` |
| R07 cumulative bars | `features/home/domain/model/CumulativeIncomeSeries.kt`, `HomeScreen.kt` |
| R08 donut/trend | `features/home/domain/model/HomeDonut.kt`, `HomeScreen.kt` |
| R09 equal cards | HomeScreen shared weighted card row |
| R10 main goal | Home goal state/viewmodel and goal-specific route |
| R11 nav depth | `core/presentation/theme/ZarElevation.kt`, floating-nav effect style |
| R12 flat cards | `ZarCards.kt`, `ZarComponents.kt`, `surfaces-and-depth.txt` |
| R13 Spending | `features/home/presentation/screen/SpendingScreen.kt`, dedicated query/viewmodel |
| R14 loading | `SessionLoadingScreen.kt`, `zar_splash_animated.xml`, task5 evidence |
| R15 registration | `RegisterScreen.kt`, shared UnderlineField, RegisterScreenUiTest |
| R16 auth motion | `AuthMotion.kt`, `AuthMotionUiTest.kt`, normal/reduced handoff tracks |

Representative screenshot review covered revised Plans, enlarged text and unresolved donut; fixed clipping/clearance in a targeted pass. Motion tracks were structurally read back; no rendered motion export or human feel acceptance is claimed. Full state-by-state device verification belongs to task6.
