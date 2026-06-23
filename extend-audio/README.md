# EXTEND Audio

EXTEND Audio is an Android MVP for local music playback with headphone-oriented EQ presets.

## Stack

- Kotlin
- Android Views (`XML + Fragments`)
- Navigation Component
- ViewBinding
- Media3 / ExoPlayer
- Room / SQLite for local persistence

## Current Status

Creating presentation of part 17 now.

Implemented direction for this stage:

- new Android Studio project from scratch
- welcome flow and main shell with bottom navigation
- library, presets, mixer, profile, and player screens
- folder-based local audio import and playback
- local persistence for tracks and saved presets
- preset state management without a real DSP engine yet

## Repository Layout

- Android application code: `android-app/`
- Theory, screenshots, and project materials: nearby folders in this repository

## Notes

- The old `Project/` app is intentionally not used as the base for this stage.
- Cloud/community features and the real equalizer engine are planned for later.
