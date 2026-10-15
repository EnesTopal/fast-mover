# 📂 Fast Mover – Android Floating File Transfer Tool

Fast Mover is a lightweight Android utility that automatically moves files between two folders. A foreground service watches the source folder, moves every new file to the destination as soon as it is fully written, and a floating bubble shows what is happening.

## 🚀 Features

- 📂 Folder selection via **SAF (Storage Access Framework)** or, for Downloads, **All files access**
- ⚡ Near-instant detection (inotify / content observers) with a cheap single-query fallback scan
- 🧘 Waits until a file stops changing before moving it – no half-written photos or downloads
- 🔁 Collisions become `name (1).ext` instead of overwriting; failed copies are cleaned up
- 🖼️ Moved files are media-scanned, so they show up in the gallery right away
- 🫧 **Floating bubble** (in-house overlay, no third-party library) with a status ring and a moved-file badge
- 📊 Expanded panel: `Source → Destination`, live status, session counter, last file, recent transfers, **Pause / Resume / Stop**
- 🔔 Foreground notification with Pause / Resume / Stop actions
- 💾 Survives process death: a sticky restart resumes with the same folders and still moves files that arrived while the app was dead
- 🧭 Jetpack Compose UI, Material 3

## 🖼️ Use

<img src="/screenshots/fast-mover.gif" width="300"/>

## ⚙️ How It Works

1. Launch the app and grant **Display over other apps** (required) – and, on Android 13+, notifications (optional).
2. Pick a source folder (any SAF folder, the DCIM/Camera shortcut, or the Downloads shortcut) and a destination folder (any SAF folder, or a new dated folder inside Pictures).
3. Tap **Start bubble service**. The app closes and the bubble appears.
4. Every file that *arrives* in the source folder from now on is moved to the destination. Files that were already there are left alone.
5. Tap the bubble for the status panel; drag it to the ⨉ target at the bottom to stop the service.

**Pause** stops moving; files that arrive while paused are *not* moved later – Resume starts from a fresh snapshot.

## 📁 Why can't I pick the Downloads folder?

Since Android 11 the system folder picker refuses to grant the **root** of `Download` (and the storage root, `Android/data`, `Android/obb`). The "Use this folder" button simply stays disabled there. There are two ways around it, and Fast Mover supports both:

| Option | What it gives you | How |
|---|---|---|
| **Sub-folder of Download** | Only that sub-folder (e.g. `Download/Telegram`) | Pick it with *Choose folder* – allowed by SAF |
| **All files access** (`MANAGE_EXTERNAL_STORAGE`) | The whole `Download` folder, using the File API + inotify | Tap the download shortcut; it opens the app's *All files access* switch in system settings |

A third option, MediaStore, only exposes *media* files and cannot delete another app's file without a system prompt, so it is on the roadmap rather than in the app.

> Google Play restricts `MANAGE_EXTERNAL_STORAGE` to file-manager-like apps. For a sideloaded / personal build this is not a problem.

## 🔐 Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | The floating bubble and its panel |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the watcher alive |
| `POST_NOTIFICATIONS` (13+) | Status notification with Pause / Stop (service runs without it) |
| `MANAGE_EXTERNAL_STORAGE` | Only for watching the whole Downloads folder |

## 🧱 Architecture

```
core/       Location (Path | Tree), AppPrefs (single settings store), TransferState (StateFlows)
watch/      SourceWatcher → FileSourceWatcher (FileObserver), SafSourceWatcher (one query per scan)
            StabilityGate (size/mtime unchanged ≥ 1.5 s), WatchEngine (snapshot → stable → move loop)
transfer/   FileMover (temp name → verify → rename, SAF create/delete, media scan), FileNames
overlay/    OverlayWindow + OverlayLifecycleOwner (fresh ComposeView + lifecycle per show),
            BubbleController (bubble / close target / panel state machine), BubbleContent, ExpandedPanel
bubble/     BubbleService (foreground service: startForeground first, pause/resume/stop commands)
uix/        MainScreen (permissions, folder pickers, service status)
```

Design notes:

- The service reads its configuration from `AppPrefs` only, never from intent extras, so a sticky restart with a `null` intent is identical to a normal start.
- A per-source **high-water mark** ("everything modified before T has been handled") is persisted after every clean scan. After a system restart, files newer than T are still moved; after a user-initiated start they are ignored.
- Every overlay show creates a brand-new `ComposeView` with its own `LifecycleOwner`; nothing is recycled, which removes the blank-bubble / dead-tap failures of the previous library-based implementation.

## 🧪 Verifying on a device

```
adb logcat -s FastMover
```

Useful scenarios: tap/back the bubble many times, rotate while the panel is open, drop the bubble on the close target, take a photo with the camera, download a large file (watch the `.crdownload` get ignored until it finishes), `adb shell am kill com.tpl.fast_mover` and check the sticky restart.

## 🗺️ Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md).

---

© 2025 Enes Topal. MIT License.
