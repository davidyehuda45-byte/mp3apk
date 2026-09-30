# YT2MP3

An Android app for saving the audio of YouTube videos as MP3 files. Everything runs on your phone. There is no server, no account, and no ads.

## How it works

Paste a video link, or share it straight from the YouTube app using the Share menu. The app shows a preview of the video, you pick an audio quality, and the download starts. The result is an MP3 file with ID3 tags, saved to a folder you choose, including an SD card. Every download is recorded in the history screen.

To fetch and convert the audio, the app uses yt-dlp (through youtubedl-android) and FFmpeg, both running directly on the device.

## Features

- Accepts links from the Share menu or the clipboard
- Selectable audio quality (bitrate)
- A download queue that runs one item at a time and picks up again after the app is killed
- Destination folder chosen through the Storage Access Framework, so no broad storage permission is needed
- Filename collisions become `Song (1).mp3`, and existing files are never overwritten
- Download history with details, retry, and per-entry delete
- In-app yt-dlp updates, with automatic rollback if an update fails
- Light and dark themes

## Acceptable use

Only use this app for content you own or are allowed to save. The app:

- does not bypass DRM or any access control. Private or restricted videos produce an error message, never a login prompt.
- does not remove or fake ownership information. The source URL is kept in the history and in the ID3 tags.
- does not send the links you download to any server. Apart from YouTube itself, the only network request is the engine update check, made over HTTPS.

You are responsible for how you use it, including following the terms of service of the platforms involved.

## Building from source

Requirements: JDK 17, Android SDK (compileSdk 34), and an internet connection for the first Gradle sync.

```
# Linux / macOS
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

Run the unit tests:

```
./gradlew :app:testDebugUnitTest
```

The debug APK is around 255 MB because it bundles Python, FFmpeg, and the yt-dlp binaries for every CPU architecture. For a release build, enable ABI splits in `app/build.gradle.kts` to bring the size down considerably. Configure a signing keystore in the same file before building a release APK:

```
./gradlew :app:assembleRelease
```

## Project structure

The project is written in Kotlin with Jetpack Compose and follows an MVVM layout. Room stores the queue and history, and Coil loads thumbnails.

- `domain/model` holds the data models: `AudioQuality`, `DownloadStatus`, `VideoMetadata`, and `AppError`
- `domain/usecase` holds the logic for fetching metadata, starting, canceling, and retrying downloads, and updating the engine
- `data/engine` wraps yt-dlp, including a fake version for tests and the engine version manager
- `data/storage` handles saving through SAF and writing ID3 tags
- `data/queue` holds the queue runner, which keeps one download active at a time
- `service` holds the foreground service that runs downloads
- `ui` holds the Home, Downloads, History, and Settings screens

## License

Not yet specified.
