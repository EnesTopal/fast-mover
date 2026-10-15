# Fast Mover – Roadmap

Ideas are grouped by how much they change the current design. Nothing here is scheduled; the list is meant to make the trade-offs explicit before picking the next piece of work.

## 1. Small, self-contained

| Feature | Notes |
|---|---|
| **Extension / size filters** | `only: jpg,mp4`, `ignore: < 50 KB`. Lives entirely in `WatchEngine.scanAndMove`; UI is a text field in the source card. |
| **Conflict policy** | Today: rename to `name (1).ext`. Add *skip* and *overwrite* as a setting passed to `FileMover`. |
| **Rename template** | `{date}_{name}`, `{counter}` – applied in `FileMover` before `uniqueName`. |
| **In-app history screen** | `AppPrefs.history` already keeps the last 50 records; show all of them with a "clear" and a share/export (CSV) action. |
| **Error notification** | On `Status.Error` post a second, non-silent notification so the user notices a lost permission even with the bubble hidden. |
| **Battery optimisation prompt** | Ask for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on aggressive OEMs (Xiaomi, Huawei, Samsung) so the sticky service really comes back. |
| **Quiet hours** | Pause automatically between two times of day; `WatchEngine` already supports being cancelled and restarted. |
| **Localisation** | Strings are hard-coded English; move them to `strings.xml` and add Turkish. |

## 2. Medium

| Feature | Notes |
|---|---|
| **Multiple rules** | Several `source → destination` pairs watched at once. `WatchEngine` is already per-source; the service would hold a list of engines and the panel a list of rows. `AppPrefs` needs a JSON list instead of two keys. |
| **Auto-start on boot** | `RECEIVE_BOOT_COMPLETED` receiver → `startForegroundService`. Android 14 allows starting a `specialUse` FGS from `BOOT_COMPLETED`. Needs an opt-in switch. |
| **Quick Settings tile** | `TileService` toggling the bubble service; nice for users who do not want the app icon. |
| **Undo last move** | Keep the last N (source, destination) pairs; "Undo" copies the file back. SAF sources need the parent tree URI, which `Location` already stores. |
| **Per-rule dated sub-folders** | Create `Pictures/<date>` automatically when the day changes instead of once from the UI. |
| **SD card / multiple volumes** | SAF already works; the File-API path assumes the primary volume. Use `StorageManager.storageVolumes` for the Downloads shortcut on secondary volumes. |

## 3. Larger / design decisions

| Feature | Notes |
|---|---|
| **MediaStore fallback for Downloads** | Watch `MediaStore.Downloads` with `READ_MEDIA_*` instead of All files access. Limitations: only media files are visible and deleting another app's file needs `MediaStore.createDeleteRequest` (a system dialog every batch), so it would be a *copy* mode. Worth it only if a Play Store release is planned. |
| **Play-compatible flavour** | A build flavour without `MANAGE_EXTERNAL_STORAGE` (SAF + MediaStore only) for Play, and the full flavour for GitHub releases. |
| **Cloud destination** | Upload instead of move (WebDAV / S3). Changes `Location` into an interface with a third implementation and makes `FileMover` pluggable. |
| **Widget** | Home-screen widget mirroring the bubble panel (count, status, pause). |
| **Material You theming** | Already dynamic-colour aware; add a proper colour scheme and a dark variant for the bubble. |

## Known limitations to keep in mind

- SAF watching polls every 4 s (one provider query); the external-storage provider only sends change notifications while a cursor is open. Camera photos therefore appear after 2–6 s. Downloads with All files access are detected within ~0.5 s via inotify.
- "Pause" discards files that arrive while paused (by design – see readme). A "catch up on resume" switch would be a one-line change in `BubbleService.resume`.
- Very large destination folders make the SAF collision check (one child query per move) slower; caching child names per scan would fix it.
