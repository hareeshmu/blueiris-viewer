# RTSP Live

Minimal always-on RTSP viewer for Android phones, tablets, and Android TV.
Point it at any RTSP stream — Blue Iris, Hikvision, Dahua, Amcrest, Reolink,
UniFi Protect, MotionEye, Frigate, or any generic ONVIF/IP camera — and it
plays it full-screen with aggressive auto-reconnect. No cloud, no account,
no ads.

Originally built after Onvifer Pro (21.53) broke against Blue Iris 6's
upgraded RTSP server — Onvifer never sends `OPTIONS` before `DESCRIBE` and
never computes Digest `Authorization` headers, so the server drops the
socket. This app uses Google's Media3 ExoPlayer, which handles the full
RTSP handshake including Digest auth correctly.

## Screenshots

<p>
  <img src="docs/screenshots/playing.png" alt="Playing a BlueIris group (4-camera mosaic)" width="640">
</p>

Playback screen — fullscreen, no chrome. This is a BlueIris group stream (`/G`) tiling four cameras into a single mosaic that BlueIris composes server-side. Long-press (touch) or press MENU / long-press OK (TV remote) to reach settings.

<p>
  <img src="docs/screenshots/settings.png" alt="Settings screen" width="640">
</p>

Settings screen.

## Features

- Single configurable RTSP stream, fullscreen, landscape-locked
- Media3 ExoPlayer 1.4.x RTSP source — proper `OPTIONS` → Digest auth → SETUP → PLAY
- **Two reliability watchdogs**:
  - *Connect watchdog*: reconnect if `STATE_READY` isn't reached within 15s
  - *Frozen-stream watchdog*: reconnect if playback position doesn't advance for 10s while supposedly playing (catches the "socket open, server stopped sending RTP" failure mode)
- Linear backoff auto-reconnect capped at 30s, with live countdown + attempt counter
- Keeps screen on, immersive full-screen, no on-screen controls
- **Settings access**:
  - Touch devices: long-press the screen
  - Android TV: press **MENU** on the remote, or **long-press OK** on D-pad (hint shown on startup)
- Auto-start on device boot (toggle)
- Adaptive launcher icon + Android TV Leanback banner

## Prerequisites

- JDK 17 (Android Studio bundles one at `/Applications/Android Studio.app/Contents/jbr`)
- Android SDK with `platforms;android-34` and `build-tools;34.0.0`
- ADB on your PATH

Env vars the build expects:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

## Build

```bash
cd ~/Workbench/rtsp-live
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk` (~9 MB).

## Install on a device

The app expects **Wireless debugging** (Android 11+) or **ADB over Wi-Fi** (older) enabled on the target device.

### Android 11+ (wireless debugging with pairing)

Tablet: *Settings → Developer options → Wireless debugging → ON* → tap *Pair device with pairing code* to get an IP:port and 6-digit code.

```bash
adb pair <tablet-ip>:<pair-port>  <code>
adb connect <tablet-ip>:<connect-port>      # connect port shown at top of wireless debugging screen
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Older Android (port 5555 direct)

Tablet: *Settings → Developer options → ADB over Wi-Fi → ON*. Then:

```bash
adb connect <tablet-ip>:5555
# accept the RSA prompt on the device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Deploy to a device (install + pre-seed config + launch)

Credentials and host live in a gitignored `.env` file at the repo root. Copy
the template once:

```bash
cp .env.example .env
# edit .env: set BI_USER, BI_PASS, BI_HOST, optional BI_DEFAULT_PATH
```

Then for each device, build the APK once and deploy per-device:

```bash
./gradlew assembleDebug
scripts/deploy.sh <adb-device-id> <stream-path>
```

Examples:

```bash
scripts/deploy.sh 192.168.1.50:36623 G         # Android 11+ wireless debug port
scripts/deploy.sh 192.168.1.51:5555 FrontDoor  # Legacy ADB-over-Wi-Fi port 5555
scripts/deploy.sh 192.168.1.52:5555            # uses BI_DEFAULT_PATH from .env
```

The script installs the APK, writes the app's SharedPreferences via `run-as`
(works on debug builds without root), launches the PlayerActivity, and prints
the RTSP trace command to tail.

`<stream-path>` is any BlueIris **short name** — either a **camera short name** or a **group short name** (groups are multi-camera mosaics, configured under *Cameras → Add a group* in BlueIris). Any alphanumeric, e.g. `G`, `FrontYard`, `AllCameras`.

### Launching

- **Touchscreen (phone/tablet)**: app drawer → RTSP Live (dark-blue camera icon). Long-press on the home screen to pin.
- **Android TV (TCL etc.)**: Home → Apps row → scroll to RTSP Live banner. If it's not on the row, *See all apps* → select it → *Add to Home*. Alternatively: *Settings → Apps → RTSP Live → Open*.

### Change settings later

- Touch devices: long-press anywhere on the video → Settings opens
- Android TV: `adb shell am start -n com.hareesh.rtsplive/.SettingsActivity` (until a D-pad settings entry is added)

## BlueIris gotchas (hard-won)

1. **Webserver → Advanced → Method**: must be `Basic (plaintext)` or Digest.
   `Secure login page only` **breaks RTSP entirely** — BlueIris tries to apply
   HTML-cookie auth to every connection including RTSP, which can't fill an HTML
   form. The server accepts the TCP socket and sends zero bytes.
2. **Windows network profile must be "Private" on the BlueIris PC.** Even with
   Windows Defender Firewall off, the "Public" profile silently drops inbound
   LAN traffic from certain IPs (while others pass). Symptom: Mac can connect,
   one tablet can't, on the same subnet. Fix: *Settings → Network & Internet →
   (connection) → Network profile → Private*. Takes effect immediately.
3. **BlueIris RTSP auth is always Digest**, regardless of the webserver auth
   method. Any client that fails to compute Digest `Authorization` headers (like
   Onvifer 21.53) will fail.
4. **BlueIris sometimes flaps** into an unresponsive state where even Mac's
   ffprobe gets silent drops. Restarting BlueIris (tray icon → Exit → relaunch)
   clears it. `Max connections` defaults to 99; dropping to 20 reduces accumulation.
5. **Stream URL format**: `rtsp://user:pass@host:81/<shortname>`, where
   `<shortname>` is either a **camera short name** or a **group short name**
   (BlueIris groups = multi-camera mosaic streams, configured under
   *Cameras → Add a group*). Port 81 is the webserver port; RTSP is
   multiplexed on the same port.

## Troubleshooting

Verify the server is talking to this device before blaming the app:

```bash
# From the device (via adb shell)
printf 'OPTIONS rtsp://<host>:81/G RTSP/1.0\r\nCSeq: 1\r\n\r\n' | nc -w 3 <host> 81
```

Expect `RTSP/1.0 401 Unauthorized` + `WWW-Authenticate: Digest realm="BlueIris"`. Zero output = server-side filtering (see gotchas 1 and 2).

Watch the app's RTSP conversation:

```bash
adb -s <device> logcat -c
adb -s <device> shell am start -n com.hareesh.rtsplive/.PlayerActivity
adb -s <device> logcat | grep RtspClient
```

Expected progression: `OPTIONS` → `DESCRIBE` → `401` with Digest → retry DESCRIBE with Authorization → `200 OK` + SDP → `SETUP` → `PLAY` → `200 OK`.

## Project layout

```
app/src/main/
├── AndroidManifest.xml              # Leanback + LAUNCHER categories, cleartextTraffic allowed
├── java/com.hareesh.rtsplive/
│   ├── PlayerActivity.kt            # ExoPlayer RTSP, fullscreen, auto-reconnect loop
│   ├── SettingsActivity.kt          # URL / TCP-UDP / reconnect delay / autostart
│   ├── Prefs.kt                     # SharedPreferences wrapper
│   └── BootReceiver.kt              # Autostart on BOOT_COMPLETED
└── res/
    ├── layout/                      # activity_player, activity_settings
    ├── values/                      # strings, themes (fullscreen)
    ├── drawable/                    # ic_launcher_*, tv_banner (all vector)
    └── mipmap-anydpi-v26/           # adaptive icon definitions
```

Prefs file on device: `/data/data/com.hareesh.rtsplive/shared_prefs/rtsp-live.xml`

## TODO (future work)

- Multiple stream URLs with D-pad / swipe to switch cameras
- PTZ controls via BlueIris JSON API (`/admin?camera=X&ptz=N`)
- Snapshot-on-tap saving to device Downloads
- Auto-update pipeline (F-Droid repo or direct APK URL)

## License

MIT — see [LICENSE](LICENSE). Free to use, modify, and redistribute; no warranty. Contributions welcome.
