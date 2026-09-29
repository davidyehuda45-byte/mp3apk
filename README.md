# YT2MP3 — Personal YouTube-to-MP3 Downloader

Direct-APK Android app (Kotlin, Compose, MVVM). Paste/share a URL → preview →
choose quality → foreground download → MP3 with ID3 → SAF folder (SD card OK) →
history. Engine (yt-dlp via youtubedl-android + FFmpeg) runs fully on-device;
no backend, no accounts, no ads.

## Legal boundaries (PRD §1.2)

Use only for content you own or are permitted to save. The app:

- does not bypass DRM or access controls (restricted/private content surfaces
  an error, never a credential prompt or bypass);
- does not strip or fake ownership metadata (source URL is kept in history/ID3);
- never sends your URLs to an app server (engine update check hits only the
  configured release API over HTTPS).

## Build

Requirements: JDK 17, Android SDK (compileSdk 34), internet for first Gradle sync.

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.12"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Verified: `assembleDebug`, `assembleRelease` (R8 + release lint), and 11 unit
tests pass. Debug APK is ~255 MB
(it bundles Python + FFmpeg + yt-dlp natives for all ABIs; use ABI splits
per `app/build.gradle.kts` for a smaller release APK).

Release:

```powershell
.\gradlew.bat :app:assembleRelease
# signing: configure keystore in app/build.gradle.kts before store release
```

Run unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Design system (PRD v1.1)

Quiet-confidence visual language, centralized tokens, no decorative effects:

- `ui/theme/Theme.kt` — restrained Material 3 palettes (near-white light,
  deep-neutral dark, single emerald accent), 3 shape sizes, plain type scale
- `ui/theme/Spacing.kt` — `AppSpacing` tokens (no ad-hoc margins)
- `ui/theme/Motion.kt` — `AppMotion` durations (150/220/350ms) + easings,
  `rememberReducedMotion()` (animator-scale = 0 collapses decorative motion)
- Native Splash Screen API, original 3-bar mark (no platform mimicry)
- Home: delayed loading indicator (300ms grace), preview fade + 8dp rise,
  Paste→Pasted feedback, segmented quality, destination-change flash,
  primary button states (Preparing / Downloading % / Converting / Completed)
- Downloads: Current + Queue hierarchy, smoothed real progress, cancel dialog
- History: Today/Yesterday grouping, compact rows, entry-only delete,
  tap → detail bottom sheet (metadata + Retry/Delete, §31)
- Rules enforced: no emoji, no gradients/glow/shimmer-everywhere/confetti,
  progress always from real data
- Errors carry a Try again action (§41); empty states link back to Home (§40)
- Low-RAM devices automatically use reduced motion (§44); list thumbnails
  are size-aware Coil requests (~112px) so lists never hold full-res
  bitmaps (§45)

## Project map (PRD §26)

- `domain/model` — `AudioQuality` (single bitrate source), `DownloadStatus`
  state machine, `VideoMetadata`, `AppError` categories
- `domain/usecase` — fetch metadata, start/cancel/retry, engine update
- `data/engine` — `YtDlpEngine` (+`FakeYtDlpEngine`), `EngineVersionManager`
- `data/storage` — SAF `StorageManager`, `Id3Writer`
- `data/queue` — sequential `QueueRunner` (one active item)
- `service` — `DownloadForegroundService` + `yt2mp3_downloads` channel
- `ui` — Home / Downloads / History / Settings + bottom navigation

## Key behaviors

- Share Sheet (`ACTION_SEND text/plain`) works on cold start, foreground, and
  background (`singleTop` + `onNewIntent`); surrounding prose is tolerated.
- Storage is SAF-only (`ACTION_OPEN_DOCUMENT_TREE`, persistable permission);
  no broad storage permission, no hard-coded `/storage/...` paths.
- Temp files stay in app cache until the MP3 is finished, then copied to the
  user tree; collisions become `Song (1).mp3`, never silent overwrite.
- Progress is real or indeterminate — never faked.
- Queue + history persist in Room; queue resumes after process restart.
- Engine update validates before activating and rolls back on failure.
