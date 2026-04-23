# Google Play listing — copy-paste ready

Draft of all text / asset specs for the Play Console submission.
Update as needed before submitting.

---

## App name (max 30 chars)
```
RTSP Live
```

## Short description (max 80 chars)
```
Always-on RTSP viewer for any IP camera or DVR. Phones, tablets, Android TVs.
```

## Full description (max 4000 chars)
```
RTSP Live is a minimal, no-frills RTSP video player for Android.

Point it at any RTSP stream — Blue Iris, Hikvision, Dahua, Amcrest, Reolink,
UniFi Protect, MotionEye, Frigate, Shinobi, or any generic ONVIF/IP camera —
and it plays the stream full-screen, landscape-locked, keeping the screen on.
Ideal as an always-on wall display on a spare tablet or Android TV.

No cloud service. No account. No analytics. No ads. The app talks directly
to the server you configure and to nothing else.

Built after Onvifer Pro (a popular ONVIF/RTSP client) stopped working against
Blue Iris 6 — Onvifer never sends RTSP OPTIONS before DESCRIBE and never
computes Digest Authorization headers, so the server drops the socket. RTSP
Live uses Google's Media3 ExoPlayer library, which handles the full RTSP
handshake (OPTIONS → DESCRIBE → SETUP → PLAY) including Basic and Digest
authentication correctly.

WHAT IT DOES
• Plays one RTSP stream full-screen with keep-screen-on
• Single stream URL — for multi-camera views, use your server's
  group/mosaic feature (Blue Iris "groups", etc.) and point this at that URL
• Works with TCP interleaved (most reliable) or UDP transport
• Auto-reconnects with visible countdown and attempt counter
• Two reliability watchdogs: reconnect on slow setup, and reconnect when the
  stream silently freezes (a failure mode ExoPlayer itself doesn't surface
  as an error)
• Optional auto-start on device boot — good for wall-mounted tablets and TVs
• Settings accessible via long-press (touch) or MENU / long-press OK on
  Android TV remotes

WHAT IT DOES NOT DO
• No camera discovery (you configure the URL directly)
• No cloud service, no account, no analytics, no ads
• No data collection of any kind — the app talks only to the server you
  configure
• No PTZ, no recording, no motion alerts (use your DVR/NVR for those)

PRIVACY
• No personal data is collected, transmitted, or shared
• Your stream URL and settings stay on the device in app-private storage
• No cloud backup of app data
• Open source (MIT license): https://github.com/hareeshmu/rtsp-live

PERMISSIONS
• Internet — to connect to your camera/DVR server
• Receive boot completed — only if you enable "Auto-start on boot"

TESTED AGAINST
• Blue Iris 6.x on Windows
• Samsung SM-P610 Android tablet (Android 13)
• TCL Android TV (Android 11)
• Any device running Android 6.0 (API 23) or higher

Source code: https://github.com/hareeshmu/rtsp-live
```

---

## Category
- **Category**: Video Players & Editors
- **Tags**: Video, Security, Home Automation

## Target audience
- Age group: 18+ (security / surveillance context avoids COPPA scope)

## Content rating
- Expected: Everyone (no violence, no profanity, no user content)
- Fill out IARC questionnaire: all "No"

## Data safety
- Data collected: **None**
- Data shared: **None**
- Data encrypted in transit: N/A (you don't collect any)
- Users can request deletion: N/A

## Privacy policy URL
```
https://hareeshmu.github.io/rtsp-live/privacy
```
(Enable GitHub Pages with /docs as source, or Pages from `docs` folder on
`main` branch.)

## Contact details
- Email: <your public email>
- Website: https://github.com/hareeshmu/rtsp-live

## App signing
- **Recommended**: Enroll in Play App Signing. Upload with your existing
  release.jks; Google holds the actual signing key for distribution.
- Your current keystore fingerprint: run
  `keytool -list -v -keystore keystore/release.jks` to get it.

---

## Graphic assets checklist

| Asset | Size | Status |
|-------|------|--------|
| App icon | 512×512 PNG, 32-bit alpha | TODO: upscale `ic_launcher_foreground.xml` |
| Feature graphic | 1024×500 PNG/JPG, no alpha | TODO |
| Phone screenshots (2–8) | ≥320px short side, 16:9 or 9:16 | Have `settings.png`, `playing.png` (resize if needed) |
| 7" tablet screenshots (1–8) | ≥320px short side | Same screenshots OK |
| 10" tablet screenshots (1–8) | ≥1080px short side | Same screenshots OK |
| Android TV screenshots (1–8) | 1920×1080 16:9 landscape | Capture from TCL TVs via adb |
| Android TV banner | 1280×720 16:9 | TODO — enlarge `tv_banner.xml` |

## Release tracks
- **Internal testing** — up to 100 testers, instant. Start here.
- **Closed testing** — the mandatory 20-tester / 14-day gate for new
  developer accounts. Required before production.
- **Open testing** — public link, optional.
- **Production** — visible on Play Store. After the closed-test gate.

---

## Pre-submit checklist

- [ ] D-U-N-S number for the business
- [ ] Play Console Organization account verified
- [ ] Privacy policy page live at public HTTPS URL
- [ ] `app-release.aab` built (use `./gradlew bundleRelease`)
- [ ] Listing text filled in
- [ ] Graphics uploaded
- [ ] Data safety form submitted
- [ ] Content rating questionnaire submitted
- [ ] Target audience declared
- [ ] Ads declaration: No
- [ ] In-app purchases: None
- [ ] 20 testers recruited for closed-track
- [ ] 14-day closed test completed
- [ ] Production release submitted for review
