# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WeightWise is an Android weight-tracking app built with Jetpack Compose. All UI text and comments are in Chinese (Simplified). The app tracks weight records and body composition (BLE body-fat scale), diet records with AI food recognition, calculates BMI/TDEE, displays trend charts, and provides AI-generated weekly/period reports via the Doubao (Volcengine Ark) API.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (minification enabled)
./gradlew assembleRelease

# Run unit tests (36 test classes: parsers, calculators, aggregators, theme contrast)
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate baseline profile
./gradlew :app:generateBaselineProfile
```

**Requirements:** JDK 21, Android SDK with compileSdk 37 / minSdk 29 / targetSdk 36, Kotlin 2.4.10, NDK (arm64-v8a), Gradle 9.5.

## Architecture

MVVM without a separate domain layer. Business logic lives in ViewModels and data objects.

```
ui/{feature}/     → Screen composables + ViewModel (StateFlow): main, diet, record, report, trend, setting
data/              → Room entities/DAOs, network, scale BLE engine, backup, workers
util/              → TimeUtils (date formatting, Beijing timezone), calculators/aggregators (TDEE, goal, streak, milestone, predictor, report)
```

### Navigation

Uses **Navigation3** (`androidx.navigation3`), not traditional Navigation Compose. Six `@Serializable` destinations defined as objects in `MainActivity.kt`: `Main`, `Setting`, `Record`, `DietRecord`, `Report`, `BodyTrend`. Navigation is imperative via `backStack.add()` / `backStack.removeAt()`.

### Dependency Injection

**Koin 4.2 with Koin Annotations** (KSP code generation). `@Module @ComponentScan` on `KoinModule` auto-discovers `@Single` and `@KoinViewModel` annotated classes. ViewModels injected in composables via `koinViewModel()`.

### Data Layer

- **Room** (version 10 database): `Record` (weight + bodyComposition JSON), `DietRecord` entities. Auto-migrations enabled, destructive migration disabled; migration 7→8 (`Migration7To8`) deleted the removed exercise-plan/journey tables.
- **MMKV**: User preferences (height, age, gender, activity level, target/start weight, reminder and weekly-report-push settings, Doubao model id, theme id, appearance mode). All values exposed as `StateFlow` via MMKV-KTX.
- **Ktor + OkHttp**: Network calls to the Doubao (Volcengine Ark) API, OpenAI-compatible protocol. SSE streaming for AI responses. API key via `BuildConfig.DOUBAO_KEY` from `secrets.properties` (git-ignored).

### Bluetooth Scale (icomon)

`data/scale/` connects to ICOMON white-label body fat scales (BLE name "icomon", service FFB0/write FFB1/notify FFB2). `IcomonFrameParser` is pure Kotlin and unit-tested against known frame variants plus frames captured from this repo's actual hardware (AC 27 variant, tag-35 firmware: grams = raw24BE − 0x8C0000, stable flag = byte2 high bit, impedance = u16BE bytes 4-5 on result frames type 0x01/0x02, profile command checksum = sum(bytes[2..18]) & 0x1F sent write-no-response). `ScaleBleEngine` drives scan→connect→subscribe→profile push→parse→insert; after the stable weight frame it waits up to 5 s for the BIA result frame. Body composition (14 metrics: fat/water/muscle mass/bone/SMM/protein/subcutaneous/visceral level/body type/body score) is computed app-side by `BodyFatCalculator` from published equations (Sun 2003 FFM, Janssen 2000 SMM, Deurenberg 1991 fallback + physiological-constant decomposition; no GPL code). Two entry points: Settings → 体脂秤 card (`ui/setting/ScaleCard.kt`, auto-insert with 2-min dedup) and the 记录体重 dialog (`ui/main/MainDialog.kt`, `autoInsert=false` — fills the wheel on Done, saves composition on user confirm, falls back to manual entry). Needs BLUETOOTH_SCAN **and** BLUETOOTH_CONNECT on Android 12+. Raw frame log mirrors to logcat tag `ScaleBle`.

### Global UI Communication

`MainActivity.kt` defines `CompositionLocal` providers for SnackBar, loading dialog, and message dialog. Access via `LocalSnackBarShow`, `LocalShowLoadingDialog`, `LocalHideLoadingDialog`, `LocalShowMessageDialog`.

## Key Conventions

- **Compose UI only** — no XML layouts, no View system.
- **Material3 with Material Expressive** theme. WeightWise 3.0「东方数据刊物」规则以 `DESIGN.md` 为准；color schemes live in `ui/theme/WeightWiseSchemes.kt`, typography and shapes are wired in `ui/theme/Theme.kt`.

## Design System

Always read `DESIGN.md` before making visual or UI decisions. Font choices, colors, spacing, page composition, and the deliberate non-Material shapes are defined there. Do not deviate without explicit user approval; UI review must flag code that falls back to uniform default cards or conflicts with `DESIGN.md`.
- **Vico 3.x** for weight trend charts (CartesianChart with line + area fill).
- **Paging 3** for weight record list (page size 20).
- **kotlinx.serialization** for JSON — all serialized models use `@Serializable`.
- ViewModels use single `MutableStateFlow<State>` pattern with `data class` state.
- Time utilities use **Beijing timezone** (UTC+8) for daily weight aggregation SQL queries.

## Release Build Notes

- `isMinifyEnabled = true`, `isShrinkResources = true`
- ProGuard rules file is empty — if adding release build issues, check kotlinx.serialization and Koin keep rules first
- Debug build suffix: `.debug` with name "体重记录-Debug"

## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Author a backlog-ready spec/issue → invoke /spec
