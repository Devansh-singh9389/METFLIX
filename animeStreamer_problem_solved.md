# Anime Player Playback & Quality Selection Resolution Report

## Executive Summary
This document details the issues encountered during anime video playback in MetFlix, their technical root causes, and the complete solutions implemented.

---

## Issue 1: ExoPlayer Playback Error (`ERROR_CODE_PARSING_MANIFEST_MALFORMED`)

### 1. Problem Description
When attempting to stream videos from Zephyrix CDN (`play.zephyrix.org`), ExoPlayer failed with the following error:
```text
Playback error (ERROR_CODE_PARSING_MANIFEST_MALFORMED):
Input does not start with the #EXTM3U header.
```

### 2. Root Cause Analysis
* **CDN Header Validation:** Zephyrix enforces strict anti-bot and CDN security checks.
* **Missing Client Hints:** ExoPlayer's `DefaultHttpDataSource` was making HTTP requests for `master.txt` without Chromium Client Hint headers (`Sec-CH-UA`, `Sec-CH-UA-Platform`, `Sec-CH-UA-Mobile`).
* **Security Error Response:** Because the required `Sec-CH-UA` headers were missing, Zephyrix rejected ExoPlayer's HTTP request and returned `"security error"` (a 14-byte string) instead of the `#EXTM3U` manifest.
* **Parser Failure:** ExoPlayer attempted to parse `"security error"` as an HLS playlist, throwing `Input does not start with the #EXTM3U header`.

### 3. Solution Applied
1. **`AnimePlayerViewModel.kt`**: Added the complete suite of Client Hint headers (`Sec-CH-UA`, `Sec-CH-UA-Platform`, `Sec-CH-UA-Mobile`, `Sec-Fetch-*`, `Accept-Language`) to `headersForPointer`.
2. **`AnimeExoPlayerView.kt`**:
   - Enforced injection of missing `Sec-CH-UA*` headers into `DefaultHttpDataSource.Factory()`.
   - Stripped duplicate `User-Agent` keys from request properties to avoid header header collisions.
   - Enabled `setKeepPostFor302Redirects(true)` so header properties persist through redirects.
3. **`AnimeStreamExtractor.kt` & `AnimeStreamSniffer.kt`**: Configured `DEFAULT_STREAM_HEADERS` and captured WebView sniffer headers with matching Client Hint properties.

---

## Issue 2: Low Video Quality & Unable to Switch Quality in Player Settings

### 1. Problem Description
The video defaulted to low quality (240p/360p) and selecting higher resolutions (e.g., 720p or 1080p) in the Anime Player settings popup had no effect.

### 2. Root Cause Analysis
1. **Hardcoded Bitrate & Size Caps:** `AnimeExoPlayerView.kt` originally configured `.setMaxVideoSizeSd()` and `.setMaxVideoBitrate(2_500_000)` at player initialization.
2. **Ignored Resolution Overrides:** When selecting "1080p", the master playlist required 4.0 Mbps (`BANDWIDTH=4096000`). Because the 2.5 Mbps bitrate cap was never cleared, ExoPlayer filtered out the 1080p track.
3. **Lack of Minimum Constraints:** Setting `setMaxVideoSize(Int.MAX_VALUE, 1080)` only set an upper bound constraint. Without setting `setMinVideoSize(1, 1080)`, ExoPlayer's Adaptive Bitrate (ABR) algorithm remained on lower quality variants (240p/360p).

### 3. Solution Applied
1. **`AnimeExoPlayerView.kt`**:
   - Removed initial SD/2.5 Mbps caps so "Auto" quality can stream full HD / 720p / 1080p natively.
   - Updated `LaunchedEffect(selectedQuality)` to set both `setMinVideoSize(1, targetHeight)` AND `setMaxVideoSize(Int.MAX_VALUE, targetHeight)` along with `setMaxVideoBitrate(Int.MAX_VALUE)` when a quality is selected.
   - Automatically cleared size and bitrate constraints when "Auto" is selected.
   - Added dynamic manifest track inspection in `onTracksChanged` to pass available stream heights to `AnimePlayerViewModel.updateAvailableQualities(...)`.
2. **`AnimePlayerViewModel.kt`**: Added `updateAvailableQualities()` to populate the quality picker dialog dynamically with the exact resolutions available in the active stream (e.g. 1080p, 720p, 480p, 360p, 240p).

---

## Verification & Results
* **Compilation:** Kotlin build compiled cleanly without errors.
* **Unit Tests:** All 25 unit tests in the project passed (`25 passed, 0 failed`).
* **Playback & Quality:** ExoPlayer plays HLS streams without manifest errors, auto-detects stream resolutions, and switches quality seamlessly when selected in the player settings popup.
