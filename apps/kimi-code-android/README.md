# Kimi Code Android

Android adaptation layer for the Kimi Code project. Minimum Android version: API 29 (Android 10). The client uses the official Kimi Code API endpoint and keeps the project organized so the Android UI can evolve toward feature parity with Kimi Code Desktop/CLI.

## Current Android foundation

- Kotlin Android application.
- API 29 minimum; resizable and sensor-based portrait/landscape support.
- English/Spanish interface selector persisted locally.
- Official Kimi Code API base URL (`api.kimi.com/coding/v1`) and K3 model.
- Workspace/session-oriented UI foundation.
- GitHub Actions APK build with a 15-day artifact retention period.

## Important parity note

Kimi Code Desktop currently carries the Agent core of Kimi Code CLI, including workspaces, sessions, approvals, tools, terminal verification, browser integration, plugins and subagents. The repository's core is TypeScript/Node.js, so a native Android APK cannot obtain full parity merely by changing an Android SDK value. The Android layer is therefore isolated under this directory; additional Agent-Core/ACP transport work is required before claiming complete feature parity.
