# Zar design system

Source: [Zar UI Figma](https://www.figma.com/design/bBBJuQz1Cg8N34FcisKBs9/Zar-UI?node-id=48-17), Design System page, Foundations light `48:17` and dark `56:172`. Values below come from Figma variable definitions and paint styles.

## Using the Compose tokens

- Wrap screens in `ZarTheme`; read semantic colors from `ZarTheme.colors`.
- Use `ZarType` for exact named text styles. `ZarTypography` maps the same styles to Material typography slots.
- Use `ZarDimens` and `ZarShapes` for spacing and corners; `ZarGradients` for the four hero paint styles.
- Geist variable and Geist Mono variable are bundled in `res/font`. Amounts use Geist Light (300); labels use Geist Mono Regular (400), uppercase, with 4% tracking.

## Main geometry

| Token | Value |
| --- | ---: |
| Space xxs / xs / sm / md / lg / xl / 2xl / 3xl | 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 dp |
| Radius xs / sm / md / lg / xl / sheet / hero / full | 8 / 12 / 16 / 20 / 25 / 28 / 32 / 999 dp |
| Screen frame | 412 × 915 |
| Status bar / gesture area | 50 / 24 dp |
| Card / hero / sheet gutter | 9 / 7 / 24 dp |
| Minimum touch target | 48 dp |
| Floating nav / add button | 68 dp tall / 68 × 68 dp |

`radius/hero` applies to the bottom corners of a full-width hero. Inset hero cards use `radius/xl` (25 dp).

## Typography

| Style | Family, weight | Size | Tracking |
| --- | --- | ---: | ---: |
| Display/Amount XL | Geist Light | 52 sp | -2% |
| Display/Amount L | Geist Light | 34 sp | -2% |
| Display/Title | Geist Light | 32 sp | -2% |
| Heading/Amount M | Geist Light | 28 sp | -2% |
| Heading/Amount S | Geist Light | 23 sp | -2% |
| Heading/Sheet | Geist Medium | 18 sp | -1% |
| Body/L, Body/M, Body/S | Geist Regular | 16, 15, 14 sp | -1%, 0%, -1% |
| Label/Button, Label/Chip, Label/Tab | Geist Medium, Medium, Regular | 15, 13, 8 sp | 0%, 0%, 1% |
| Mono/Label, Mono/Caption, Mono/Axis | Geist Mono Regular | 11, 10, 10 sp | 4%, 0%, 2% |

Figma line height is 100% for named text styles. Currency prefixes beside amounts use 55% opacity.

## Color and paint

The exact light and dark semantic palette and raw primitives are in `ZarDesign.kt`. The light canvas is `#F5F5F5`, surface `#FFFFFF`, primary text `#0E0F12`, and accent primary `#0C1625`. The dark canvas is `#07080B`, surface `#111318`, primary text `#F3F3F5`, and accent primary remains `#0C1625`.

`ZarGradients.dusk`, `received`, `spent`, and `goal` preserve Figma's exact color stops, including stops past 100%, so the visible lower edge stays dark. Use them only for hero money surfaces. The `statusBarScrim` brush is the separate dark status bar overlay.

Figma elevation is neutral: card shadows are `#0F1A2B0A` (0, 1, blur 2) and `#0F1A2B0F` (0, 10, blur 28, spread -10); floating nav shadows are `#0F1A2B0F` (0, 2, blur 6) and `#0F1A2B1A` (0, 14, blur 34, spread -4), with 12 dp background blur.

## Screen coverage

The shared theme, controls, navigation, and icon resources apply across the app. Home, More, Accounts, Account Detail, and Settings use the Figma spacing and type scale directly. Figma's sample amounts, account freshness, and goal progress are design examples; screens continue to show the values the app actually has.

The ongoing Add, Activity, reconciliation, and Plans stories (SCRUM-35–38 and SCRUM-47–48) retain their current feature implementations. Their owners can consume the shared tokens as those stories finish.

## Appearance

The saved Light / Dark / System choice (Settings → Appearance, `DisplayPreferencesRepository`) is resolved once in `MainActivity`, above `ZarRoot`, by `ZarAppearance`. It feeds `ZarTheme`, the system-bar glyphs and the activity window background. Light is used until a choice is saved; Android's own mode is only consulted when System is selected. `MainActivity` declares `configChanges="uiMode"`, so an Android theme change recomposes in place instead of recreating the activity.

The system splash that Android 12+ draws before the app starts reads the manifest theme, which cannot see the saved choice. `Theme.Zar` is therefore Light-only, matching the Light default; everything the app draws itself uses the resolved appearance from the first frame.

## Motion policy

`MotionPolicy` (`core/presentation/motion`) is the single answer to "may this move?". `ProvideZarMotion` sits under `ZarAppearance` around `ZarRoot`, so both the signed-in and auth graphs read it through `LocalZarMotion`. It combines the saved Reduce motion switch with Android's animator duration scale (0 means system animations are off) into one `MotionLevel`: `Full`, `Reduced` (app switch: no spatial travel, bounce or press scaling; crossfade at most 100 ms) or `Instant` (system animations off: final state at once). Screens never read either setting directly. Spring values live in `ZarSprings`.

## Baseline proportions (prototype, not applied)

Goal: with Android's Font size and/or Display size one step below default, keep the accepted default composition. `ZarProportionPolicy` (`core/presentation/theme/BaselineProportions.kt`) is the prototype. `ProvideBaselineProportions` is **not** wired into `MainActivity` yet.

| Input | Rule |
| --- | --- |
| Display size below default (`densityDpi` < `DisplayMetrics.DENSITY_DEVICE_STABLE`) | Lay out with the stable density. One dp is then the baseline pixel count and the window has the baseline logical width. |
| Display size at or above default | Unchanged. Larger display sizes reflow instead of being compensated. |
| Font size below 1.0 | Floored at 1.0. |
| Font size above 1.0 | Passed through untouched, so Android's own (non-linear on Android 14+) scaling applies. |
| Stable density unknown | Only the font floor applies; no claim of physical invariance. |

It overrides only Compose's `LocalDensity`; it never changes Android's settings or configuration, does not scale a bitmap and does not read xdpi/ydpi. Insets, the IME and pointer coordinates are converted with the same density, so touch bounds follow what is drawn. Interactive targets stay at least `ZarDimens.MinTouchTarget` (48 dp), which is never smaller than at default because density is only ever raised.

Known: the repo's Pixel 6a AVD profile is 1080 x 2400 px at 420 dpi, i.e. 2.625 density and 411.4 dp wide, which matches the 412 x 915 Figma frame. That is an emulator profile, not a measurement of the physical phone.

Not yet known, and why this is not applied: (1) that `DENSITY_DEVICE_STABLE` equals the density behind the accepted default-size screenshots on the physical Pixel 6a; (2) the real density and font scale of "one step smaller"; (3) how dialog and sheet windows and system pickers behave under the override. Verification checklist for the physical phone: record `densityDpi`, `DENSITY_DEVICE_STABLE`, `fontScale`, window and usable bounds, Android version and insets for default/default, small/default, default/small and small/small with one fixture and both themes; then apply the provider at the root and compare hero height, Add diameter, navbar height, card gutters and key text bounds against the default screenshots (within 5%), including the sheets, the keyboard and the date and time pickers.
