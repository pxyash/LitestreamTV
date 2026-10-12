# LITESTREAM TV

A native Android TV starter based on the supplied LITESTREAM Space Gray & Powder Blue HTML design. It uses Kotlin, Jetpack Compose, TV-friendly focusable controls, AndroidX Media3, and a GitHub Actions debug APK workflow.

## Current implementation

- Native TV-style navigation: Home, Movies, TV Shows, Search, My Library, Settings.
- Demo catalog inspired by the supplied HTML categories and titles.
- Detail screen, in-memory bookmark interaction, search, three appearance themes.
- Media3 player wiring for a configured direct media URL.
- Android TV launcher intent and banner/icon resources.
- GitHub Actions build/test workflow that uploads a debug APK artifact.

## Important limitations

The HTML source uses sample catalog metadata and generates mock stream specifications rather than resolving actual playable stream URLs. This native starter deliberately does not invent stream URLs. The demo items are not playable until a legitimate, compatible `playbackUrl` is supplied by a provider or configured for a title. Provider manifest installation, durable Room/DataStore persistence, real metadata APIs, Trakt OAuth, subtitles UI, and watch-progress restoration remain follow-up implementation work; do not treat those as completed features.

Poster blocks are local stylized placeholders in this starter. The original HTML is preserved as `source-design.html` for reference.

## Build in GitHub Actions

1. Create a new GitHub repository named `LitestreamTV`.
2. Upload/commit the contents of this folder to the repository root, including `.github/workflows/build-apk.yml`.
3. Open **Actions** and select **Build LITESTREAM TV APK**.
4. Select **Run workflow** (or push a commit to `main`/`master`).
5. After a successful run, download **LitestreamTV-debug-apk** from the run's Artifacts section. Extract it to get `app-debug.apk`.

The workflow installs Gradle 8.9 directly and does not require a checked-in Gradle wrapper. For local builds, install Gradle 8.9 and Android SDK platform 35, then run:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install on Android TV

Copy the debug APK to the TV or use Android Debug Bridge with USB/network debugging enabled, then run:

```bash
adb install -r app-debug.apk
```

## Media URL configuration

For a controlled playback test, set a real, authorized, compatible HTTPS media URL on a `MediaItem`'s `playbackUrl`. The player screen only opens for items with a non-null URL. Do not commit private provider tokens or secrets.

## Version

Starter project 1.0.0. This is a foundation for development, not a claim that every feature described in the original HTML is fully ported.
