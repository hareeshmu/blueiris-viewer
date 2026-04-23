# blueiris-viewer — project notes for Claude

Minimal Android app: single configurable RTSP stream, fullscreen, auto-reconnect. Built to replace Onvifer on the user's tablets + TCL Android TVs that monitor BlueIris DVR cameras.

## Why this exists

Onvifer Pro 21.53 broke after BlueIris 6 upgrade. Its RTSP client:
- Sends `DESCRIBE` as the first method (skips `OPTIONS`)
- Sends `Authentication: None` even after receiving 401 Digest challenges
- Never computes Digest `Authorization` headers

Result: BlueIris closes the socket after 10s silence → "Connection socket has been closed by device." A bug report was sent to Biyee (Onvifer dev); no fix expected soon.

## Architecture (keep it small)

- `PlayerActivity` — fullscreen activity, `PlayerView` + `ExoPlayer` with `RtspMediaSource`. Listens to `onPlayerError` and `onPlaybackStateChanged`; on error/STATE_ENDED schedules a reconnect with linear backoff (capped at 30s).
- `SettingsActivity` — four inputs: URL, TCP/UDP radio, reconnect delay, autostart checkbox. No validation beyond integer parsing.
- `Prefs` — thin `SharedPreferences` wrapper returning an immutable `StreamConfig` data class.
- `BootReceiver` — launches `PlayerActivity` on `BOOT_COMPLETED` if `autoStart && url.isNotBlank()`.

Do not add a camera-list DB, multi-stream grid, or foreground service. The whole point is single-stream simplicity. If scope grows, create a new app.

## Non-obvious build/install facts

- JDK 17 required. Android Studio's bundled JBR is at `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Gradle wrapper was generated with `gradle wrapper --gradle-version 8.7` (AGP 8.5.2 is not compatible with Gradle 9).
- SDK path is in `local.properties` (gitignored). Create it on fresh clones: `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties`.
- Media3 **1.4.1** is the pinned version. RTSP + Digest auth landed in 1.2.x; 1.4.x is current stable. Downgrade carefully — 1.1.x does not handle Digest.

## Pre-seeding prefs for fleet deploy

Writing SharedPreferences from adb is the trick that makes deploying to many devices painless. The app is debug-signed, so `run-as com.hareesh.blueirisviewer` works. See README "Pre-seed" section for the full incantation.

Prefs XML lives at `/data/data/com.hareesh.blueirisviewer/shared_prefs/blueiris-viewer.xml`. Keys: `url` (string), `transport_tcp` (bool), `reconnect_seconds` (int), `autostart` (bool). Keep these exact names — `Prefs.kt` reads them verbatim.

## BlueIris environment assumptions

Target BlueIris setup:
- Server: BlueIris on Windows, webserver port 81
- Port 81 multiplexes webserver + RTSP on the same port
- Stream path format: `/<shortname>` — matches each camera's "short name" in BlueIris (any alphanumeric). `/G` is conventional for the group mosaic; per-camera short names are user-defined (e.g. `N`, `E`, `FrontDoor`, `Driveway`).
- Credentials: per-user, LAN-only
- Auth: Digest always for RTSP, regardless of the Webserver `Method` dropdown

Server-side config that **must** be correct (otherwise stream is dead for every client, not just this app):

1. Webserver → Advanced → **Method: Basic (plaintext)** — not "Secure login page only"
2. Windows network profile on BlueIris PC = **Private** — "Public" profile silently drops some inbound LAN traffic even with firewall off

## Troubleshooting flow

When a device shows "Connecting…" and never plays:

1. Raw RTSP test from the device via `adb shell` + `nc`:
   ```
   printf 'OPTIONS rtsp://<host>:81/G RTSP/1.0\r\nCSeq: 1\r\n\r\n' | nc -w 3 <host> 81
   ```
   Expect `401` + Digest challenge. Zero output = server-side problem.
2. If raw nc fails from device but succeeds from Mac on same LAN: it's the Windows network profile. Flip to Private.
3. If raw nc fails from everywhere: BlueIris itself is flapping. Restart it (tray → Exit → relaunch).
4. If raw nc succeeds from device but app doesn't play: logcat for `RtspClient` tag. Debug logging is already enabled (`setDebugLoggingEnabled(true)` in PlayerActivity).

## Deployed devices

The per-fleet IP ↔ stream-path mapping lives in `fleet.md` (gitignored). Append
a row there when you deploy a new device; don't track it in public docs.

Deploy an additional device with `scripts/deploy.sh <adb-id> <stream-path>`
(reads `.env` at repo root). Don't reinvent the pre-seed incantation inline.

## Things to resist

- **Do not add ONVIF discovery.** Onvifer's ONVIF probe is exactly what broke this flow originally (SOAP calls to non-ONVIF cameras). This app is RTSP-only on purpose.
- **Do not add HTTP Basic as an RTSP auth fallback.** Media3's RTSP source uses HTTP auth mechanisms transparently; adding manual Authorization headers fights the library.
- **Do not move to ExoPlayer's `ProgressiveMediaSource` or HLS by default.** BlueIris does expose an HLS endpoint (`http://host:81/h264/<shortname>/temp.m3u8`) but it's per-session with cookies and not as real-time as RTSP-over-TCP.
- **Do not switch to Compose UI yet.** AppCompat + XML is adequate for a four-widget settings screen and a single PlayerView. Compose adds compile time and APK size for no visible benefit here.
