<div align="center">
  <img src="assets/Auric_Music.jpg" alt="Auric Music Logo" width="140"/>

  <h1>Auric Music</h1>

  <p><strong>A robust, open-source music streaming client offering an ad-free experience, offline capabilities, and advanced music discovery.</strong></p>

  <a href="https://trendshift.io/repositories/15844" target="_blank">
    <img src="https://trendshift.io/api/badge/repositories/15844" alt="Trendshift" width="250" height="55"/>
  </a>

<br>

  <a href="https://auricmusic.tndev.in/download">
    <img src="assets/download.png" alt="Download" width="200"/>
  </a>
  &nbsp;
  <a href="https://auricmusic.tndev.in/obtainium">
    <img src="assets/obtainium.png" alt="Get it on Obtainium" width="200"/>
  </a>
</div>

---

## Overview

Auric Music delivers a seamless, premium listening experience by leveraging YouTube Music's vast library — without the ads. It adds powerful extras including offline downloads, real-time synchronized lyrics, and environment-aware music recognition.

---

## Screenshots

<div align="center">
  <img src="Screenshots/sc_1.png" alt="Home Screen" width="200"/>
  <img src="Screenshots/sc_2.png" alt="Music Player" width="200"/>
  <img src="Screenshots/sc_3.png" alt="Playlist Management" width="200"/>
  <img src="Screenshots/sc_4.png" alt="Settings" width="200"/>
  <img src="Screenshots/sc_5.png" alt="Settings" width="200"/>
</div>

---

## Features

### Streaming & Playback
- **Ad-Free** — Stream without interruptions.
- **Seamless Playback** — Switch effortlessly between audio-only and video modes.
- **Background Playback** — Listen while using other apps or with the screen off.
- **Offline Mode** — Download tracks, albums, and playlists via a dedicated download manager.

### Discovery & Auric Find
- **Auric Find** — Identify songs playing around you using advanced audio recognition.
- **Smart Recommendations** — Personalized suggestions based on your listening history.
- **Comprehensive Browsing** — Explore Charts, Podcasts, Moods, and Genres.

### Advanced Capabilities
- **Synchronized Lyrics** — Real-time synced lyrics with AI-powered multilingual translation.
- **Sleep Timer** — Set automatic playback stop after a chosen duration.
- **Cross-Device Support** — Cast to Chromecast devices or stream via DLNA/UPnP.
- **Data Import** — Import playlists and library data from other services.

---

## Installation

### Android
Download the latest APK from the [Releases Page](https://github.com/NirmaLKumar26/Auric-Music/releases/latest).

### Build from Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/NirmaLKumar26/Auric-Music.git
   cd Auric-Music
   ```

2. **Configure Android SDK**
   ```bash
   echo "sdk.dir=/path/to/your/android/sdk" > local.properties
   ```

3. **Firebase configuration**
   Firebase is required for analytics and reliable imports. See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) for instructions on adding your `google-services.json`.

4. **Build**
   ```bash
   ./gradlew assembleFossDebug
   ```

---

## Community & Support

Join the community for updates, discussions, and help.

<div align="center">
  <a href="https://discord.gg/dZ8WQakFps"><img src="assets/discord.png" width="140"/></a>
  &nbsp;
  <a href="https://t.me/"><img src="assets/telegram.png" width="130"/></a>
</div>

---

## Support the Project

If Auric Music has been useful to you, consider supporting its development.

<div align="center">
  <a href="https://github.com/NirmaLKumar26/Auric-Music"><img src="assets/bmac.png" width="140"/></a>
  &nbsp;
  <a href="https://github.com/NirmaLKumar26/Auric-Music"><img src="assets/upi.svg" width="100"/></a>
  &nbsp;
  <a href="https://github.com/NirmaLKumar26/Auric-Music"><img src="assets/patreon3.png" width="100"/></a>
</div>


## Special Thanks

Auric Music stands on the shoulders of several excellent open-source projects. Sincere thanks to:

| Project | Description |
|---------|-------------|
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | Foundational inspiration and architecture reference |
| [Better Lyrics](https://better-lyrics.boidu.dev/) | Lyrics enhancement and synchronization |
| [SimpMusic](https://github.com/maxrave-dev/SimpMusic) | Lyrics implementation reference |
| [Music Recognizer](https://github.com/aleksey-saenko/MusicRecognizer) | Audio recognition (Echo Find) |

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=NirmaLKumar26/Auric-Music&type=timeline&legend=top-left)](https://www.star-history.com/#NirmaLKumar26/Auric-Music&type=timeline&legend=top-left)

---

<div align="center">
  Licensed under <a href="LICENSE">GPL-3.0</a>
</div>
