# Glyphies — notes for Claude

## Working with the owner
- The owner does not do manual steps (GitHub UI, git commands, CI buttons). Do everything
  yourself; if an action truly needs their authorization, send them the exact link
  (GitHub access: https://claude.ai/connect-github) instead of instructions.
- They install and update from their phone (a Nothing Phone (4a) Pro): the in-app updater
  follows published releases.
- Sibling project: kream0/ytune (same design system, updater, CI and Glyph Matrix plumbing).
  Keep the two consistent when touching shared pieces.

## Releasing
- Bump `VERSION` (e.g. `0.1.0` → `0.1.1`) and push to the working branch. CI tags that
  commit `vX.Y.Z` and publishes the GitHub release with `glyphies.apk` + `version.json`.
- Plain pushes only build a test APK (Actions artifact); the app never offers those.
- Release when a user-facing change is ready and they asked for it (e.g. "release it",
  "ship it"), and say which version went out.

## Building
- This sandbox can't reach Google's Maven/SDK hosts, so Android builds only run in GitHub
  Actions (`.github/workflows/build.yml`). Push, then read the run with the GitHub MCP tools;
  the "Show compiler errors" step prints Kotlin errors compactly.
- The Glyph Matrix SDK AAR is downloaded at build time (pinned + SHA-256) because its licence
  forbids redistribution: never commit `app/libs/`.
- The engines are pure Kotlin (`engine/`, `glyph/MatrixShape.kt`, `glyph/DotFont.kt`,
  `glyph/DotText.kt`): compile them with a standalone kotlinc (GitHub release zip of
  JetBrains/kotlin) together with a small `main()` and run them on the JVM to test physics and
  games (print frames as ASCII) before pushing.

## Design rules
- Every UI string is written in both languages where it's used: `tr("English", "Français")`.
- Colours come from the palette `P` (dark / paper); one red accent; dot-matrix (Doto) for
  display text, Space Grotesk for text, Space Mono caps for labels.
- Matrix coordinates: x right, y down, as seen on the back of the phone. Sensors are turned
  into matrix coordinates in `Sensors.snapshot()` (the "You look at" setting mirrors x).
- Frame values are brightness 0..255; values 1..9 are maze markers (`engine/Marks`), never
  real brightness (the pen's dimmest level is 50).
