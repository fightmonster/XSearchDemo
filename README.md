# XSearchDemo

A universal search Android app for **Android 14+** that lets you search across contacts, media files, documents, and installed apps — all in one place.

## Features

- 🔍 **Contacts** — search by name or phone number
- 🖼️ **Media** — search images, videos, and audio files
- 📄 **Documents** — search PDF, DOCX, XLS, ZIP and more via SAF
- 📱 **Apps** — launch Chrome, Maps, YouTube, WeChat and more
- ⚙️ **Settings** — jump directly to Wi-Fi, Bluetooth, Battery, etc.
- 🎙️ **Voice search** — search hands-free

## Permissions

| Permission | Purpose |
|---|---|
| `READ_CONTACTS` | Search contacts |
| `READ_MEDIA_IMAGES` | Search image files |
| `READ_MEDIA_VIDEO` | Search video files |
| `READ_MEDIA_AUDIO` | Search audio files |

Documents folder is accessed via **Storage Access Framework (SAF)** — no `MANAGE_EXTERNAL_STORAGE` required.

## Requirements

- Android **14+** (API 34)
- No root required

## Build

```bash
./gradlew assembleDebug
```

## Release

Releases are built and signed automatically via **GitHub Actions**.  
Download the latest APK from [Releases](../../releases).

## License

MIT
