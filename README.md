# LITESTREAM TV — Liquid Glass build

Native Android TV starter app built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Implemented in this version

- Liquid-glass-inspired TV UI with translucent gradient panels, reflective borders, D-pad focus states, and Space Gray / AMOLED Black / Deep Ocean themes.
- Add-on manager for installing and removing Stremio-compatible `manifest.json` URLs.
- Reads add-on manifest metadata and declared catalogs, then requests `/catalog/{type}/{id}.json` and displays returned meta previews/posters.
- Local title search over the catalogs currently loaded in the app.
- Requests `/stream/{type}/{id}.json` from installed compatible add-ons and displays direct HTTP/HTTPS stream URLs returned by those add-ons.
- Media3 player with HLS and DASH modules, progressive HTTP/HTTPS playback, request headers from Stremio `behaviorHints.proxyHeaders.request`, built-in controls, and track selection when exposed by the media.
- Installed add-ons and saved library IDs are stored locally on the device.
- GitHub Actions builds a debug APK and uploads it as an artifact.

## Build an APK

1. Push the repository to GitHub.
2. Open **Actions** → **Build LITESTREAM TV APK** → **Run workflow**.
3. Download the `LitestreamTV-debug-apk` artifact from a successful workflow run.

## Install an add-on

1. Open **Add-ons** in the app.
2. Paste the full URL to an add-on's `manifest.json` (for example, `https://addon.example/manifest.json`).
3. Choose **Install add-on**. The app fetches the manifest and then loads the catalogs declared by it.
4. Open a title and choose one of the returned direct stream URLs to play it.

Only install add-ons you trust. Prefer HTTPS. HTTP is enabled for compatibility with user-supplied HTTP manifests/streams, so traffic to those endpoints is not encrypted.

## Compatibility boundaries — please read

This is a working integration foundation, not a universal Stremio runtime:

- It currently handles direct HTTP/HTTPS stream URLs. Torrent hashes, `ytId`, magnet links, external-player links, and add-ons that require a custom resolver are not converted to playable URLs.
- Series episode navigation, add-on-specific configurable forms, add-on catalog search/pagination, external subtitle URL injection, Trakt sign-in/sync, and advanced DRM configuration are not implemented yet.
- HLS (`.m3u8`), DASH (`.mpd`), and progressive HTTP/HTTPS are supported through Media3. HDR output depends on the TV/box, Android version, decoder, codec profile, bit depth, stream packaging, and display pipeline. No app can promise every codec or HDR format on every device.
- DRM-protected streams only play when their DRM scheme/license setup is supported and configured; this version does not add per-add-on DRM configuration.
- Some add-ons return browser-only, geo-restricted, expiring, or header-sensitive links. These may not work in an Android player even if they work elsewhere.
- Catalog endpoints with mandatory `extra` properties may need more add-on-specific handling than the default catalog request implemented here.

## Project structure

- `app/src/main/java/com/litestream/tv/MainActivity.kt` — TV UI, add-on manager, details and playback screens.
- `app/src/main/java/com/litestream/tv/data/AddonRepository.kt` — manifest, catalog and stream HTTP client plus local persistence.
- `app/src/main/java/com/litestream/tv/data/MediaItem.kt` — catalog item models.
- `source-design.html` — original HTML design reference.

## Notes

- The project is configured for Android SDK 35 and JDK 17.
- The debug APK is for testing/sideloading, not a signed production release.
- Build success should be verified by the GitHub Actions workflow after each update.
