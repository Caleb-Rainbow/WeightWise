# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WeightWise is an Android weight-tracking app built with Jetpack Compose. All UI text and comments are in Chinese (Simplified). The app tracks weight records, calculates BMI, displays trend charts, and provides AI-generated daily exercise plans via the DeepSeek API.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (minification enabled)
./gradlew assembleRelease

# Run unit tests (currently placeholder only)
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate baseline profile
./gradlew :app:generateBaselineProfile
```

**Requirements:** JDK 21, Android SDK with compileSdk 36, NDK (arm64-v8a), Gradle 8.13.

## Architecture

MVVM without a separate domain layer. Business logic lives in ViewModels and data objects.

```
ui/{feature}/     → Screen composables + ViewModel (StateFlow)
data/              → Room entities/DAOs, network, repositories
util/              → TimeUtils (date formatting, Beijing timezone)
```

### Navigation

Uses **Navigation3** (`androidx.navigation3`), not traditional Navigation Compose. Four `@Serializable` destinations defined as objects in `MainActivity.kt`: `Main`, `Setting`, `Record`, `ExercisePlan`. Navigation is imperative via `backStack.add()` / `backStack.removeAt()`.

### Dependency Injection

**Koin 4.2 with Koin Annotations** (KSP code generation). `@Module @ComponentScan` on `KoinModule` auto-discovers `@Single` and `@KoinViewModel` annotated classes. ViewModels injected in composables via `koinViewModel()`.

### Data Layer

- **Room** (version 4 database): `Record`, `DailyPlan`, `ExerciseCompletion` entities. Auto-migrations enabled, destructive migration disabled.
- **MMKV**: User preferences (height, target weight, exercise preferences). All values exposed as `StateFlow` via MMKV-KTX.
- **Ktor + OkHttp**: Network calls to DeepSeek API. SSE streaming for AI responses. API key via `BuildConfig.DEEPSEEK_KEY` from `secrets.properties` (git-ignored).

### Bluetooth Scale (icomon)

`data/scale/` connects to ICOMON white-label body fat scales (BLE name "icomon", service FFB0/write FFB1/notify FFB2). `IcomonFrameParser` is pure Kotlin and unit-tested against known frame variants plus frames captured from this repo's actual hardware (AC 27 variant, tag-35 firmware: grams = raw24BE − 0x8C0000, stable flag = byte2 high bit, impedance = u16BE bytes 4-5 on result frames type 0x01/0x02, profile command checksum = sum(bytes[2..18]) & 0x1F sent write-no-response). `ScaleBleEngine` drives scan→connect→subscribe→profile push→parse→insert; after the stable weight frame it waits up to 5 s for the BIA result frame. Body composition (14 metrics: fat/water/muscle mass/bone/SMM/protein/subcutaneous/visceral level/body type/body score) is computed app-side by `BodyFatCalculator` from published equations (Sun 2003 FFM, Janssen 2000 SMM, Deurenberg 1991 fallback + physiological-constant decomposition; no GPL code). Two entry points: Settings → 体脂秤 card (`ui/setting/ScaleCard.kt`, auto-insert with 2-min dedup) and the 记录体重 dialog (`ui/main/MainDialog.kt`, `autoInsert=false` — fills the wheel on Done, saves composition on user confirm, falls back to manual entry). Needs BLUETOOTH_SCAN **and** BLUETOOTH_CONNECT on Android 12+. Raw frame log mirrors to logcat tag `ScaleBle`.

### Global UI Communication

`MainActivity.kt` defines `CompositionLocal` providers for SnackBar, loading dialog, and message dialog. Access via `LocalSnackBarShow`, `LocalShowLoadingDialog`, `LocalHideLoadingDialog`, `LocalShowMessageDialog`.

## Key Conventions

- **Compose UI only** — no XML layouts, no View system.
- **Material3 with Material Expressive** theme. Custom color scheme in `ui/theme/Color.kt`.
- **Vico 3.x** for weight trend charts (CartesianChart with line + area fill).
- **Paging 3** for weight record list (page size 20).
- **kotlinx.serialization** for JSON — all serialized models use `@Serializable`.
- ViewModels use single `MutableStateFlow<State>` pattern with `data class` state.
- Time utilities use **Beijing timezone** (UTC+8) for daily weight aggregation SQL queries.

## Exercise Plan System

The exercise feature has a sophisticated fallback chain:
1. AI-generated plan via DeepSeek API (`ExercisePromptBuilder` builds prompts from user data)
2. Local fallback from `ExerciseCatalog` (23 exercises across 3 difficulty levels)
3. `ExercisePreferences` supports blacklist/whitelist tags and scene filtering (indoor/outdoor/office)
4. Difficulty adjustment based on user fitness level (stored in MMKV)

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
