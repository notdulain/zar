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

The exact light and dark semantic palette and raw primitives are in `ZarDesign.kt`. The light canvas is `#F5F5F5`, surface `#FFFFFF`, primary text `#0E0F12`, and accent primary `#0C1625`. The dark canvas is `#07080B`, surface `#111318`, and primary text `#F3F3F5`.

### Approved dark overrides

Dark keeps every brand hue and changes lightness only. These are the semantic overrides from the reviewed dark Figma handoff (`add-appearance-preference`), flagged by token and contrast on the surface they actually sit on:

| Token (`ZarColors`) | Light | Dark (overridden) | Measured pair |
| --- | --- | --- | --- |
| `textIncome` | teal/600 `#2A7A80` | teal/300 `#5FB3B9` | 7.64:1 on surface `#111318` (was 3.71) |
| `textIncomeOnTint` (new) | teal/700 `#23676C` | teal/300 `#5FB3B9` | 5.64 / 6.28:1 on `infoTint` |
| `textAttentionStrong` (new) | coral/800 `#A9573F` | coral/500 `#DD8468` | ≥4.5:1 text; `textAttention` stays graphic/large only (2.77 on white) |
| `accentPrimary` | navy/900 `#0C1625` | navy/400 `#41799C` | 4.23:1 on canvas; white label 4.73:1 |
| `accentSelected` | navy/600 `#1E3A55` | navy/400 `#41799C` | 3.93:1 on surface |
| `chartCommitted` | navy/600 | navy/400 | ≥3:1 mark on the dark card |
| `chartUnknown` | gray/250 `#E6E6EA` | gray/650 `#7A7B82` | 4.41:1 on surface (removes the white glare) |
| `textPlaceholder` | gray/500 | gray/600 `#8B8C93` | 5.55:1 on surface (was 2.92) |
| `borderSelected` | navy/600 | navy/300 `#5A86A6` | 4.77:1 selected/focus outline |
| `borderNav` (new) | gray/0 `#FFFFFF` | night/750 `#272B33` | floating-nav outline |

New primitives: navy/300 `#5A86A6`, navy/400 `#41799C`, teal/300 `#5FB3B9`, teal/700 `#23676C`, coral/800 `#A9573F`. `ContrastTest` measures every pair above so the values cannot drift.

`ZarGradients.dusk`, `received`, `spent`, and `goal` preserve Figma's exact color stops, including stops past 100%, so the visible lower edge stays dark. Use them only for hero money surfaces. The `statusBarScrim` brush is the separate dark status bar overlay.

Figma elevation is neutral: card shadows are `#0F1A2B0A` (0, 1, blur 2) and `#0F1A2B0F` (0, 10, blur 28, spread -10); floating nav shadows are `#0F1A2B0F` (0, 2, blur 6) and `#0F1A2B1A` (0, 14, blur 34, spread -4), with 12 dp background blur.

## Screen coverage

The shared theme, controls, navigation, and icon resources apply across the app. Home, More, Accounts, Account Detail, and Settings use the Figma spacing and type scale directly. Figma's sample amounts, account freshness, and goal progress are design examples; screens continue to show the values the app actually has.

The ongoing Add, Activity, reconciliation, and Plans stories (SCRUM-35–38 and SCRUM-47–48) retain their current feature implementations. Their owners can consume the shared tokens as those stories finish.

## Appearance

The saved Light / Dark / System choice (Settings → Appearance, `DisplayPreferencesRepository`) is resolved once in `MainActivity`, above `ZarRoot`, by `ZarAppearance`. It feeds `ZarTheme`, the system-bar glyphs and the activity window background. Light is used until a choice is saved; Android's own mode is only consulted when System is selected. `MainActivity` declares `configChanges="uiMode"`, so an Android theme change recomposes in place instead of recreating the activity.

The window, and the Android 12+ splash it draws before the process starts, read the manifest theme and cannot see the saved choice. `Theme.Zar` therefore uses a neutral launch surface — the brand navy `@color/zar_launch_surface` `#0C1625` — that reads the same in Light and Dark, so a saved Dark choice on a Light phone (or the reverse) never flashes the opposite mode. `MainActivity` replaces the window background with the resolved canvas colour as soon as it starts, so everything the app draws itself follows the saved Appearance from the first frame. Bar glyphs on the launch surface are light; `ZarAppearance` re-applies the resolved bar style (and any screen's own hero-bar choice) once Compose is running.

## Motion policy

`MotionPolicy` (`core/presentation/motion`) is the single answer to "may this move?". `ProvideZarMotion` sits under `ZarAppearance` around `ZarRoot`, so both the signed-in and auth graphs read it through `LocalZarMotion`. It combines the saved Reduce motion switch with Android's animator duration scale (0 means system animations are off) into one `MotionLevel`: `Full`, `Reduced` (app switch: no spatial travel, bounce or press scaling; crossfade at most 100 ms) or `Instant` (system animations off: final state at once). Screens never read either setting directly. Spring values live in `ZarSprings`.

## Baseline proportions (applied; physical measurement pending)

Goal: with Android's Font size and/or Display size one step below default, keep the accepted default composition. `ZarProportionPolicy` (`core/presentation/theme/BaselineProportions.kt`) is applied once at the app root by `ProvideBaselineProportions` in `MainActivity`. It only ever raises Compose's layout density (from a below-default Display size back to `DENSITY_DEVICE_STABLE`) and floors a below-default Font size at `1.0`; at or above default it is a no-op that provides the same `Density` object. Enlarged font scales pass through untouched, so Android's non-linear scaling still grows text. Because Compose converts window insets and pointer coordinates through the same density, the drawn content, the safe areas, the IME and hit targets stay aligned; the raised density means targets are never physically smaller than at default.

| Input | Rule |
| --- | --- |
| Display size below default (`densityDpi` < `DisplayMetrics.DENSITY_DEVICE_STABLE`) | Lay out with the stable density. One dp is then the baseline pixel count and the window has the baseline logical width. |
| Display size at or above default | Unchanged. Larger display sizes reflow instead of being compensated. |
| Font size below 1.0 | Floored at 1.0. |
| Font size above 1.0 | Passed through untouched, so Android's own (non-linear on Android 14+) scaling applies. |
| Stable density unknown | Only the font floor applies; no claim of physical invariance. |

It overrides only Compose's `LocalDensity`; it never changes Android's settings or configuration, does not scale a bitmap and does not read xdpi/ydpi. Insets, the IME and pointer coordinates are converted with the same density, so touch bounds follow what is drawn. Interactive targets stay at least `ZarDimens.MinTouchTarget` (48 dp), which is never smaller than at default because density is only ever raised.

Known: the repo's Pixel 6a AVD profile is 1080 x 2400 px at 420 dpi, i.e. 2.625 density and 411.4 dp wide, which matches the 412 x 915 Figma frame. That is an emulator profile, not a measurement of the physical phone.

Still unverified on hardware, so no physical-invariance claim is made: (1) that `DENSITY_DEVICE_STABLE` equals the density behind the accepted default-size screenshots on the physical Pixel 6a; (2) the real density and font scale of "one step smaller". Emulator-verified on 26 October 2026 (`BaselineProportionsDensityTest`, Pixel_6a API 37): with a below-default display size the override raises the layout density in the real composition (a 48dp control measures 96px at the resolved density 2.0 instead of 72px at 1.5) and hit targets stay at least 48dp; Android's non-linear font conversion still applies at 1.3/1.5/2.0. A Compose `Dialog` is its own window and does **not** inherit the composition's `LocalDensity`, so dialog content is measured at the platform (window) density — recorded, not assumed. System pickers and permission dialogs likewise keep Android's native sizing. Verification checklist for the physical phone: record `densityDpi`, `DENSITY_DEVICE_STABLE`, `fontScale`, window and usable bounds, Android version and insets for default/default, small/default, default/small and small/small with one fixture and both themes; then compare hero height, Add diameter, navbar height, card gutters and key text bounds against the default screenshots (within 5%), including the sheets, the keyboard and the date and time pickers.
