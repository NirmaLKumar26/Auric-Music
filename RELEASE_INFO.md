# Auric Music Releases

This document tracks all available releases for Auric Music.

## [v5.0.0] - 2026-09-01 (Latest)
[Download on GitHub](https://github.com/NirmaLKumar26/Auric-Music/releases/tag/v5.0.0)

**Added**
- JioSaavn playback: search, play, collections, URL import, and deep links.
- **Play from** setting (YouTube Music or JioSaavn). When JioSaavn is selected, catalog songs are matched and streamed from JioSaavn.
- **JioSaavn Quality** setting: 96 / 160 / 320 kbps.
- In-app GitHub auto-update: checks the latest GitHub release on launch, then downloads and installs the APK in the app.

**Changed**
- If a song is not available on JioSaavn while Play from is set to JioSaavn, playback falls back to YouTube Music.
- Support is UPI only (`nirmalkumar.palanisamy@ptyes`). Buy Me a Coffee, Patreon, and cryptocurrency options were removed.

**Fixed**
- In-app updater now finds GitHub APKs (including arm64 builds) instead of opening the website.
- Update notifications and the Update button open the in-app updater.

## [v1.2.2] - 2026-08-28
[Download on GitHub](https://github.com/NirmaLKumar26/Auric-Music/releases/tag/v1.2.2)

**Bug Fixes**
- Fixed a crash that occurred when adding a song to a playlist, album, or artist before it was fully loaded.
- Fixed a crash caused by outdated saved settings after an app update; the app now falls back to a safe default instead of crashing.
- Fixed Spotify login issues where signing in with Google, Apple, or Facebook could show a black screen or fail to complete.
- Fixed the "Update Available" dialog not matching the app's overall theme and styling.

**Design Improvements**
- Updated input fields and dialog buttons (including in Spotify Import) to use a more rounded, modern look consistent with Material You design.

**Other Changes**
- Updated select app components to their latest stable versions for improved reliability.

## [v1.2.1] - 2026-08-28
[Download on GitHub](https://github.com/NirmaLKumar26/Auric-Music/releases/tag/v1.2.1)

I am pleased to announce the initial release of the updated Auric Music repository.

Recently, the project was subjected to a legal takedown notice. Since then, I have taken all necessary actions and made the required adjustments to the codebase and documentation to ensure full legal compliance. 

With these changes complete, I am excited to restore access to the project. I would like to extend my deepest gratitude to all of you for your unwavering support and patience during this period. Thank you for standing by me.

---

## 📋 Pull Request & Release Note Guidelines

**ATTENTION CONTRIBUTORS:** To maintain a clean and standardized changelog, all community contributions added to this file MUST strictly follow this format:

`- \`<type>(<scope>): <summary>\` ([#PR_NUMBER](URL)) by @username`

- **PR Titles** must follow [Conventional Commits](https://www.conventionalcommits.org/).
- **Descriptions** must be clear, concise, and professional.
- PRs that do not follow this strict formatting will **not** be merged.
