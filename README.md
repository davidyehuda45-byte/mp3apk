# YT2MP3 — Pengunduh Audio/Video Publik (1000+ Situs)

Aplikasi Android langsung-jadi-APK (Kotlin, Compose, MVVM). Tempel/share link →
preview → pilih format → unduh di foreground → tersimpan via SAF (SD card OK) →
riwayat. Engine (yt-dlp via youtubedl-android + FFmpeg) jalan penuh di HP;
tanpa server, tanpa akun, tanpa iklan.

## Yang bisa dilakukan (v1.4.0)

- **Situs**: link publik apa pun yang didukung yt-dlp (1000+ situs) — YouTube
  (video, Shorts, live, playlist, channel, Music, podcast), TikTok, Instagram,
  Facebook, X/Twitter, Reddit, Pinterest, Vimeo, Dailymotion, Twitch,
  SoundCloud, Bandcamp, Mixcloud, Bilibili, dan lainnya. Daftar populer ada di
  tombol "Situs didukung" di layar Home.
- **Audio**: MP3 (64–320 kbps), M4A asli, OPUS, OGG, FLAC, WAV.
- **Video**: MP4/MKV/WEBM, 144p–4K, subtitle (embed/terpisah, pilih bahasa),
  chapters, SponsorBlock, normalisasi loudness, thumbnail terpisah.
- **Batch**: tempel banyak link sekaligus, import TXT/CSV/JSON, range
  (mis. `1-10,15`), lewati yang sudah ada, subfolder per playlist, profil
  preset, pola nama file (`{title} - {artist} [{id}]`).
- **Antrean**: paralel 1–3, prioritas (Top/naik/turun), auto-retry, jeda saat
  bukan Wi-Fi, batas kecepatan, jadwal harian, RSS channel publik
  (auto-download video baru, maks 5).
- **Library**: pemutar bawaan (audio/video + sleep timer), favorit, cari,
  sortir, cek duplikat, rename, bagikan file/link, buka dengan aplikasi lain.
- **Tampilan**: terang/gelap/AMOLED, Material You dinamis, mode sederhana
  (default) vs advanced, chip pintasan situs (YouTube/TikTok/IG/FB/X/
  SoundCloud, ikon Material tanpa emoji), tempel otomatis
  dari clipboard (opt-in), tile Quick Settings, App Shortcuts.
- **Pengaturan**: menu per-halaman (Penyimpanan, Output, Antrean, Jadwal,
  Tampilan, Privasi, Lanjutan, Engine, Bantuan, Background, Legal).
- **Privasi**: kunci PIN + biometrik, mode incognito (tanpa riwayat), proxy
  (bisa Tor/Orbot), User-Agent kustom, argumen yt-dlp/FFmpeg kustom,
  hapus metadata, log + diagnostik, backup/restore pengaturan.
- **Otomasi**: broadcast `com.example.yt2mp3.action.ENQUEUE` (untuk
  Tasker/MacroDroid) dengan extra `String[] urls`.

## Batasan legal & etika

Hanya untuk konten publik yang kamu miliki atau diizinkan menyimpan untuk
dipakai offline pribadi. Aplikasi ini:

- **tanpa login/cookies**: konten privat, umur-terbatas yang butuh akun,
  berbayar, dan DRM TIDAK didukung — muncul sebagai error, tidak pernah ada
  prompt kredensial atau bypass;
- tidak menghapus watermark dan tidak memalsukan metadata kepemilikan
  (URL sumber selalu disimpan di riwayat/ID3);
- tidak mengirim URL kamu ke server mana pun (cek update engine hanya ke
  release API yang dikonfigurasi via HTTPS).

## Build

Butuh: JDK 17, Android SDK (compileSdk 34), internet untuk Gradle sync pertama.

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.12"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Terverifikasi: `assembleDebug`, `assembleRelease` (R8 + release lint), dan
**54 unit test lolos, 0 gagal**. APK rilis (ABI split): arm64 ~57 MB,
armeabi-v7a ~51 MB (berisi Python + FFmpeg + yt-dlp natives).

Rilis:

```powershell
.\gradlew.bat :app:assembleRelease
# signing: kunci rilis tetap via keystore.properties (di-gitignore, tidak ikut push)
```

Jalankan unit test:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Sistem desain

Bahasa visual yang tenang, token terpusat, tanpa efek dekoratif:

- `ui/theme/Theme.kt` — palet Material 3 (terang near-white, gelap netral,
  AMOLED hitam pekat, satu aksen emerald), warna dinamis opsional
- `ui/theme/Spacing.kt` — token `AppSpacing` (tanpa margin acak)
- `ui/theme/Motion.kt` — durasi `AppMotion` (150/220/350ms) + easing,
  `rememberReducedMotion()` (animator-scale = 0 mematikan motion dekoratif)
- Native Splash Screen API
- Home: indikator loading tertunda (300ms), preview fade + naik 8dp,
  Paste→Pasted, pilihan format audio/video, opsi lanjutan (subtitle, chapters,
  SponsorBlock, normalisasi), profil, range, tombol status
  (Preparing / Downloading % / Converting / Completed)
- Downloads: Current + Queue, progres asli, dialog cancel, prioritas,
  retry semua, bersihkan selesai
- History: grup Today/Yesterday, cari/sortir/favorit, baris ringkas,
  ketuk → bottom sheet (metadata + Play/Retry/Delete/Rename/Share)
- Aturan: tanpa emoji, tanpa gradien/glow/shimmer/confetti,
  progres selalu dari data asli
- Error selalu ada aksi Try again; empty state menautkan balik ke Home
- Perangkat RAM kecil otomatis reduced motion; thumbnail list dimuat
  hemat memori via Coil (~112px)

## Peta proyek

- `domain/model` — `AudioQuality`/`AudioFormat`/`VideoFormat`/`VideoQuality`/
  `MediaKind`/`SubtitleMode`/`PostOptions`/`DownloadProfile`, `DownloadStatus`
  state machine, `VideoMetadata`, `AppError`
- `domain/usecase` — fetch metadata, start/cancel/retry (+retry semua),
  engine update; `StartDownload` menerapkan lewati-ada + pola nama file
- `data/engine` — `YtDlpEngine` (audio + video, tuning retry/rate/proxy/UA/
  custom args tanpa login), `EngineVersionManager`
- `data/storage` — SAF `StorageManager` (subfolder, rename, hapus file,
  usage, `.nomedia`), `Id3Writer`
- `data/queue` — `QueueRunner` paralel (maks 3), Wi-Fi only, auto-retry,
  incognito, thumbnail/subtitle sidecar
- `data/util` — `UrlValidator` (generik), `PlaylistRange`, `FileNamePattern`,
  `ArgSplitter` (buang token login/DRM), `Profiles`, `RssFeed` (publik),
  `SupportedSites`, `AppLog` (buffer + viewer)
- `service` — `DownloadForegroundService` (notifikasi + aksi Open),
  `EnqueueReceiver` (otomasi), `ScheduleReceiver` + `ScheduleManager`,
  `DownloadTileService`, `QueueResumeReceiver` (reboot + reschedule)
- `ui` — Home (batch/profil/range/advanced) / Downloads / History+Player /
  Settings (semua preferensi) / Lock (PIN+biometrik) + navigasi bawah

## Perilaku kunci

- Share Sheet (`ACTION_SEND text/plain`) jalan saat cold start, foreground,
  dan background (`singleTop` + `onNewIntent`); teks di sekitar link ditoleransi.
- Storage hanya SAF (`ACTION_OPEN_DOCUMENT_TREE`, izin persistable);
  tanpa izin storage luas, tanpa path `/storage/...` hard-code.
- File temp di cache aplikasi sampai selesai, lalu disalin ke folder pengguna;
  tabrakan jadi `Lagu (1).mp3`, tidak pernah timpa diam-diam.
- Progres asli atau indeterminate — tidak pernah dikarang.
- Antrean + riwayat persisten di Room (migrasi s/d v4); antrean lanjut
  setelah restart proses/reboot (termasuk jadwal ulang alarm).
- Update engine divalidasi sebelum aktif dan rollback bila gagal.
- Changelog: lihat dialog "Changelog" di Settings.
