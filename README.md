# METFLIX 🎬

A modern Android TV streaming application built with **Jetpack Compose for TV**, **Material 3 for TV**, **Hilt**, and **Media3 (ExoPlayer)**, upgraded for modern build tooling.

## Features

- 📺 **Android TV Native Experience**: Built specifically for 10-foot TV interfaces with D-pad navigation and focus handling.
- 🎨 **Jetpack Compose for TV**: Rich declarative UI utilizing Compose TV Foundation and TV Material (`androidx.tv:tv-material`).
- ⚡ **Featured Carousel & Media Rows**: Smooth hero carousel banner, Top 10 rankings, categories, and content rows.
- 🎥 **Media3 ExoPlayer Streaming**: Immersive full-screen video playback with custom TV playback controls and seek bar.
- 🔍 **Search & Filters**: Content discovery by category, search queries, and watchlist/favourites filters.
- 👤 **Profile & Settings**: Account switching, preferences, and custom TV dialog components.
- 💉 **Dependency Injection**: Integrated with Google Hilt & KSP.

## Tech Stack & Tooling

- **Language:** Kotlin 2.2.10
- **Build System:** Gradle 9.6 with Android Gradle Plugin 9.4.1
- **UI:** Jetpack Compose (BOM 2026.02.01) + Compose for TV 1.1.0
- **Dependency Injection:** Dagger Hilt 2.56.2 + KSP 2.2.10-2.0.2
- **Image Loading:** Coil 2.7.0
- **Media Playback:** AndroidX Media3 (ExoPlayer & UI Compose) 1.8.0
- **Serialization:** Kotlinx Serialization JSON 1.8.1

## Building and Running

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17+ (Java 21 / 25 compatible)
- Android SDK 37 (Min SDK 26)

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
