# AGENTS.md — Slap

Instructions for any coding agent or tool working on this repository (Claude Code reads it through `CLAUDE.md`). Personal setup of the maintainer's machine (paths, signing, accounts) lives in `AGENTS.local.md`, which is not versioned; read it too when it exists.

## What this is
Android sticker maker for WhatsApp (and static packs for Telegram). Package `dev.tvnclr.slap`, GPL-3.0, public repo `tvnclr/Slap`. Part of the tvnclr app family: shared design system in the `tvnclr-ui/` git submodule (`dev.tvnclr.ui`).

## Branches
- `dev`: all work lands here. Test builds are published as GitHub prereleases `v<base>-dev.N`.
- `main`: only published releases.
- `fdroid`: `main` plus one commit that removes `MlKitSegmenter.java` and the `play-services-mlkit-subject-segmentation` dependency. After each release: merge `main` into `fdroid`, tag `vX.Y.Z-fdroid`.

## Layout
- `app/.../engine/` — the engine, mostly plain Java: `StickerMaker` (entry point for the UI), `BgRemover` (flat color), `MaskOps` (mask cleanup and mask application, pure arrays), `GifAi`/`AiCutout` (AI cutout), `Segmenter`/`Segmenters` (`U2NetSegmenter` on LiteRT, `MlKitSegmenter` on ML Kit), `GifDecoder`, `StickerEncoder`, `AnimatedWebp`, `PackStore`, `StickerProvider` (WhatsApp content provider), `TextLayer`/`TextLayersView`/`Fonts`, `Backup`, `Telegram.kt`, `BenchActivity` (test bench, see below).
- `app/.../ui/` — Jetpack Compose screens: `SlapApp` (navigation), Home, Pack, Editor (+ `EditorController`), Settings, About, Share.
- `app/src/main/assets/` — fonts (OFL / Apache, licenses in `licenses/`), `models/u2netp.tflite` (rebuilt by `tools/convert-u2netp.sh`).
- Each sticker keeps its source and settings (`packs/<id>/src/<s>.webp.src` + `.json`) so it can be edited again.

## Build and test
- JDK 17, Android SDK 36, Gradle wrapper. `./gradlew testDebugUnitTest assembleRelease`.
- Version: `baseVersion` in `app/build.gradle.kts`. Outside `main` the versionName is `<base>-dev.N`, N = commits since the last stable tag (dev tags excluded). Commit before building.
- Unit tests (JVM): `MaskOpsTest`, `BgRemoverTest`, `U2NetMathTest`, `RoutingTest`, `TranslationsTest`. The engine keeps its math on plain arrays so it can be tested here.
- Every new string needs `values/` and `values-b+pt+BR/` with the same arguments (`TranslationsTest` fails the build otherwise).

## Test bench (on a device)
`engine/BenchActivity` is not exported and not linked from any screen. With root (`su`) or a rooted emulator:
1. Copy test images into `/data/data/dev.tvnclr.slap/files/bench/in/` (owned by the app's user).
2. `am start -n dev.tvnclr.slap/.engine.BenchActivity`.
3. Results in `files/bench/out/` (`results.tsv`, one PNG per method, the stickers) and `files/bench/diag.txt` (self-test, every line OK or FAIL). Progress in logcat, tag `Slap/bench`.
It runs every file through flat color (both modes) and AI with each engine in the build, timing each one, and builds the sticker the editor would save.

## Known platform trap
On an arm64 phone with Android 17 the compiled code of a big method that turned the mask from boolean to byte arrays and back stopped clearing pixels after a few runs (the interpreter, x86 and debug builds were fine). `BgRemover` keeps one boolean mask from start to end, with each pass in its own small method. Check changes to the cutout code with the bench's `diag.txt` on a real arm64 device, not only on the emulator.

## Limits that matter
- WhatsApp: 3–30 stickers, 512 px, WebP up to 100 KB static / 500 KB animated, up to 10 s.
- Telegram: static import only (WebP/PNG 512 px).

## Do not
- Commit signing material (`*.jks`, `keystore.properties`) or anything listed in `.gitignore`.
- Push to `main` outside a release, or delete prereleases.

## State
- 1.0.0 and 1.1.0 released; `dev` is ahead with the AI engines (U²-Net next to ML Kit) and the cutout fixes listed in `CHANGELOG.md`.

## Next step
See the top entry of `CHANGELOG.md`.