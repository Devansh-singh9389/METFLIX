# METFLIX 🎬

A modern, high-performance Android TV streaming and Live TV application built from the ground up for the 10-foot television experience using **Jetpack Compose for TV**, **Material 3 for TV**, **Media3 (ExoPlayer)**, and **Dagger Hilt**.

---

## 🚀 Key Architectural Features & Recent Updates

### 1. ⚡ On-Device Standalone Stream Decryption (Zero-Server Architecture)
* **Native Cryptographic Engine:** Integrated pure-Java **TweetNaCl** (`XSalsa20-Poly1305`) encryption directly into the Android TV app.
* **No Laptop / No Python Backend Required:** Eliminates external local servers or Cloudflare tunnels. The TV generates timestamped authorization tokens and negotiates directly with upstream APIs (`vidlink.pro`).
* **Direct CDN Video Streaming:** Extracts direct progressive MP4 streams (1080p, 720p, 480p, 360p) and multi-language SRT/VTT subtitles directly into ExoPlayer.
* **CDN Anti-Bot & Hotlink Bypass:** 
  - Standardized ExoPlayer user-agent (`AndroidXMedia3/1.8.0`) to avoid HTTP 428 bot blocks from CDN edge servers.
  - Stripped referer headers on direct video endpoints to bypass HTTP 429 hotlink protection.
  - Bypassed redundant probe GET requests to prevent multi-gigabyte buffer allocations on low-memory TV chipsets.

### 2. 🎧 Multi-Server Dual-Audio Engine (Hindi + English) & Auto-Selection
* **Authentic Dual-Audio Streaming:** Solves the single-language problem on Hollywood blockbusters (e.g., *Avengers: Infinity War*, *Avatar*, *Spider-Man*) by pulling multi-audio media streams packaged with Hindi and English audio tracks.
* **Rank #1 Default Server:** Ranks Server 1 (Dual-Audio • Hindi + English) as the top server with an automatic `+600L` scoring bonus for Hollywood and regional blockbusters.
* **Automatic Hindi Audio Selection:** Immediately switches playback to Hindi when a Dual-Audio stream is detected on player startup.
* **Deterministic Track Selection:** Utilizes ExoPlayer `TrackSelectionOverride` to target exact track groups and track indices, avoiding string mismatches on undefined (`und`) or raw MKV audio streams.
* **Multi-Source Aggregation Pipeline:**
  - Multi-Source Engine integration (`TMDB-Embed` / Stremio stream aggregator).
  - High-speed direct CDN streams (`StreamFlix Direct` multi-language catalogs).
  - Direct Indian media decoders (`Vega` / `HubCloud` direct CDN streams).
* **Zero Laptop Dependency:** Bypasses ISP sinkholes directly on-device using Cloudflare/Google DNS-over-HTTPS (`SecureDns.kt`).

### 3. 🏎️ Parallel Server Racing & Smart Ranking
* **Concurrent Multi-Server Probing:** Queries all configured streaming providers concurrently via Kotlin Coroutines (`channelFlow` / `async`).
* **Instant First-Valid-Stream Playback:** Emits the first responding functional stream immediately to minimize user wait time to <500ms.
* **Intelligent Score & Health Engine:**
  - Evaluates ping latency, HTTP status, and direct stream vs. embed availability.
  - **Language-Aware Weighted Ranking:** Detects TMDB `original_language` (e.g., Hindi, Telugu, Spanish, French) and boosts servers offering regional audio or IPTV VOD matches.
* **In-Player Server Switcher:** Real-time overlay showing server latency, speed ratings (`Ultra`, `Fast`, `Normal`), and status badges (`Hindi+Eng`, `VIP`, `Direct`, `Regional`, `Offline`).

### 4. 📺 Live TV & EPG Integration (JioTV / IPTV)
* **Comprehensive M3U/HLS Pipeline:** Integrated live TV repository with high-performance channel loading and category filtering (Entertainment, Movies, Sports, News, Music, Kids, Regional).
* **Verified EPG Timeline Guide:** Uses provider-supplied XMLTV schedules with normalized channel matching. If a provider does not supply a verified schedule, the guide displays that program information is unavailable instead of inventing a show title.
* **Dual-Mode TV Engine:**
  - Full-screen channel zapping with custom channel drawer.
  - VOD channel matching: Cross-references TMDB movie titles against live/VOD channel streams for regional releases.
* **Custom M3U Playlist Manager:** Support for custom user-supplied IPTV playlists and streams.

### 5. 🎛️ Advanced In-Player Experience & Settings Overlay
* **Dual Playback Engines:** Uses AndroidX Media3 ExoPlayer for direct HLS/MP4/MKV streams and a TV-optimized WebView engine for embed-based providers.
* **Reliable Source Switching:** The in-player server selector stops the previous source, resolves the selected server, and reloads the correct playback engine without allowing stale resolver results to replace a newer selection.
* **Remote TV Menu Key Toggle:** Open or close the playback settings dialog with the remote's `Menu` or `Settings` button while direct or WebView playback is active.
* **Full-Featured D-Pad Settings Dialog:**
  - **Resolution Switching:** Seamlessly toggle between 1080p FHD, 720p HD, 480p, and 360p progressive tracks.
  - **Multi-Language Subtitles:** Dynamic SRT/VTT parser with customizable cue rendering, prioritized default language selection, and socket protection against memory exhaustion.
  - **Audio Track Selector:** Displays active track checkmark (`✓ Hindi (5.1 Surround)`) and channel details (Mono, Stereo, 5.1 Surround, 7.1 Surround).
  - **Playback Adjustments:** Speed adjustment (0.5x – 2.0x), Aspect Ratio / Content Scale modes (Fit, Crop, Fill, 16:9, 4:3), and Sleep Timer (15m – 120m).
  - **Real-Time Stream Diagnostics:** Live stats overlay displaying buffer health (ms) and video bitrate (Mbps).
* **Embed-Aware Settings:** Native-only track, quality, and speed controls are not applied to WebView playback; the embedded provider manages those features.

### 6. 🎌 Dedicated Anime & Animation Ecosystem (Sub & Dub)
* **Comprehensive Anime Discovery:** Native tab indexing Trending Anime Series, Popular Anime Movies (Studio Ghibli, Makoto Shinkai, *Doraemon Movies*), Hindi Dubbed Cartoons (*Doraemon*, *Shinchan*, *Naruto*, *Dragon Ball*), and Top Rated Anime.
* **Otaku & Prism Architecture:** Dedicated multi-audio anime scraper (`AnimeScraper.kt`) resolving master `.m3u8` manifests with embedded Japanese, Hindi, and English audio channels (`play.zephyrix.org`, `megacloud`, `animeworld`).
* **Multi-Season & Episode Navigation:** First-class support for series with interactive Season tabs (Season 1, 2, 3...) and scrollable Episode cards with previews, duration, and direct single-click playback.

### 7. 🎯 Android TV UI/UX Optimization
* **10-Foot Focus Navigation:** Custom D-pad event interceptors, focus memory requesters, and smooth scrolling for television remotes.
* **Low-Memory Optimization:** Engineered to run smoothly on budget Android TV hardware (MediaTek MT5862 / 512MB RAM) by preventing frame drops, avoiding heavy background thread allocations, and leveraging memory-conscious image caching.
* **Immersive TV Catalog:** Hero banner carousel, Top 10 rankings, personalized watchlist, category chips, and detailed movie/show info with trailer previews.

---

## 🛠️ Tech Stack & Tooling

| Component | Technology | Version |
|---|---|---|
| **Language** | Kotlin | 2.2.10 |
| **Android SDK** | Min SDK 26 • Target SDK 37 | API 26 / 37 |
| **Build System** | Gradle + Android Gradle Plugin | Gradle 9.6 • AGP 9.4.1 |
| **UI Framework** | Jetpack Compose for TV + TV Material | BOM 2026.02.01 • TV Material 1.1.0 |
| **Media Engine** | AndroidX Media3 (ExoPlayer, HLS, DASH, UI) | 1.8.0 |
| **Dependency Injection** | Dagger Hilt + KSP | Hilt 2.56.2 • KSP 2.2.10-2.0.2 |
| **Cryptography** | TweetNaCl (XSalsa20-Poly1305) | Native In-App |
| **Networking** | OkHttp + DNS-over-HTTPS (DoH) | 4.12.0 |
| **Image Loading** | Coil Compose | 2.7.0 |
| **Serialization** | Kotlinx Serialization JSON | 1.8.1 |

---

## 🏗️ Project Architecture

```
com.example.metflix/
├── data/
│   ├── entities/           # Movie, MovieDetails, Video, TV channel models
│   ├── network/
│   │   ├── api/            # TMDB API service & HTTP client configuration
│   │   └── resolvers/      # Stream providers, TweetNaCl crypto, VidLink & IPTV resolvers
│   └── repositories/       # MovieRepository, LiveTvRepository, StreamResolverRepository
├── presentation/
│   ├── common/             # TV cards, posters, loading spinners, carousel banners
│   ├── screens/
│   │   ├── dashboard/      # Navigation bar & top-level TV container
│   │   ├── home/           # Featured banner, trending rows, Top 10 list
│   │   ├── livetv/         # Live TV channel list, EPG timeline, playlist dialog
│   │   ├── movies/         # Movie discovery, filters, and movie details view
│   │   ├── search/         # Voice/text search interface
│   │   └── videoPlayer/    # ExoPlayer/WebView playback, source switching, settings, stats
│   └── theme/              # TV focus styling, palette, typography, shapes
└── tvmaterial/             # Custom TV modal dialogs, focus modifiers, and layout helpers
```

---

## 📦 Building and Installing

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17+ (Java 21 / 25 compatible)
- Android TV device or emulator with ADB debugging enabled

### Build Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install to Android TV over ADB
Connect your TV to the same Wi-Fi network and install directly:
```bash
adb connect <YOUR_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run Focused Unit Tests
```bash
./gradlew testDebugUnitTest --tests com.example.metflix.presentation.screens.videoPlayer.ResolutionRequestTrackerTest
./gradlew testDebugUnitTest --tests com.example.metflix.presentation.screens.livetv.player.LiveTvStreamSelectionTest
```

These tests cover stream-resolution request ordering and the Live TV stream fallback selection behavior.

---

## 📄 License
This project is open-source and released under the [Apache License 2.0](LICENSE).
