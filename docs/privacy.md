---
title: Privacy Policy — RTSP Live
---

# Privacy Policy — RTSP Live

_Last updated: 2026-04-23_

**RTSP Live does not collect, store, transmit, or share any personal
data, usage analytics, diagnostics, location, or device identifiers.**

## What the app does

RTSP Live is a single-stream RTSP video player. It connects only to the
server (URL) you configure in the app's Settings screen. All video data flows
directly between your device and that server over your local network or your
own internet connection — it does not pass through any server operated by the
author, and is not logged, copied, or forwarded anywhere else.

## Data handled on your device

The app stores the following locally on your device, in the app's private
storage (never transmitted off-device):

- The RTSP stream URL you enter (may include username and password if you
  embed them in the URL per the standard RTSP scheme)
- Transport preference (TCP or UDP)
- Reconnect delay (seconds)
- Auto-start-on-boot preference

This data is held in the Android SharedPreferences system, which is private to
the application. The app declares `android:allowBackup="false"` in its
manifest, so this data is NOT included in any Android automatic cloud backup.

## Permissions

- `INTERNET` — to connect to the RTSP server you configure
- `RECEIVE_BOOT_COMPLETED` — only if you enable the "Auto-start on boot"
  option, to relaunch the app after device reboot

The app does not request location, camera, microphone, contacts, storage, or
any other sensitive permission.

## Third-party services

None. The app does not use analytics, crash reporting, advertising, or any
cloud service.

## Children's privacy

The app is not directed at children and does not knowingly collect data from
anyone. In fact, it does not collect any data from anyone.

## Open source

The app is open source under the MIT License. Source is public at
<https://github.com/hareeshmu/rtsp-live> — you can audit exactly what
the app does.

## Contact

Questions or concerns: open an issue at
<https://github.com/hareeshmu/rtsp-live/issues> or email the address
listed on the repository.
