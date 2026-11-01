# Reports 84-90: individual triage

Follow-up implementations and corrected codec attribution are documented in
[pending-video-shaders-codecs.md](pending-video-shaders-codecs.md). The table
below records the initial triage, not the final verification status.

No world, mod, instance or configuration has been deleted. No renderer, Java
version or mod has been silently replaced. A diagnostic is not a native port.

| Evidence | Cause / status |
| --- | --- |
| log3.txt and log3 (1).txt | Identical SHA-256. These are Word binary documents despite their suffix; their UTF-16 content contains a 26.3 report. Crash offset SDL 0xb390c matches Android_JNI_PollInputDevices in the retained release symbols. New asynchronous ART-attached input-poll wrapper avoids the embedded JVM JNI call and avoids joining while SDL holds the joystick lock. Device verification pending. |
| latestlog (84) | Same SDL 0xb390c input-poll failure; covered by the new wrapper. SurfaceManagerFactory appears earlier but is not the fatal frame. |
| latestlog (85) | HunterProtection loads Windows kernel32 from its client entrypoint. Now detected by class entry, even if the JAR is renamed, before launch with the existing opt-in disable dialog. Ordinary Krypton network/renderer is not blocked. No Windows API emulation has been implemented. |
| latestlog (86) | TouchController 0.2.1-beta15 text measurement clamps into [0,-5]. The patch agent replaces only relocated integer coerceIn calls in this specific TextMeasurerImpl class with empty-width-safe clamping. Regression verifies the original fixture throws and transformed bytecode returns zero; normal constraints remain unchanged. CreamyKeys JavaSound output warnings are separate and are not fixed by this change. |
| latestlog (87) | Forge 47.4.26 + OptiFine I6 cannot load LoadingOverlay. Official OptiFine lists Forge 47.2.18 for I6. Added specific diagnosis recommending the supported pairing; no class-loading failure is suppressed and no version downgraded automatically. A successful launch with the matched pair remains unverified. |
| latestlog (88) | OptiFine 1.21.4 preview + GL4ES: malformed entity-outline program followed by missing rendertype_text, also old LWJGL mismatch warnings. Existing shader-resource repair and coordinated LWJGL bundles are present, but this exact preview/resource combination has NOT been reproduced. Do not label it solved solely because the build passes. |
| latestlog (89) | MoreCosmetics includes glibc-linked opus4j/rnnoise4j/speex4j/lame4j natives (libm.so.6), then fails in AWT Component.initIDs while reporting the error. Added explicit native-incompatibility diagnosis. Android JNI ports of those codecs and AWT UI compatibility are still required; not solved by renaming libc or adding empty native methods. |
| latestlog (90) | SHA-256 identical to latestlog (81). DeX shutdown crash already wrapped by the earlier device-query fix. Real-device exit verification remains pending. |
| Screenshot: Forge 1.12.2 catalog | Missing local loader metadata, not a Mojang vanilla catalog entry. Now explains the missing file and offers Versions to reinstall the same loader without altering the instance. A refresh race also rechecks local metadata before failing. Missing Forge JSON is not fabricated from the vanilla manifest. |
| Video | 9.18-second recording of Minecraft 26.2 with 146 mods: menu geometry stretches/moves while Android controls remain in place. No matching 26.2 log or exact control layout is provided. Keep open until reproduced with that layout and mod set; do not claim a 26.3-only patch covers it. |

## Verification

- 298 Android unit tests pass, including transformed TouchController fixture,
  Windows-client detection and loader-profile classification.
- Debug APK builds for arm64-v8a, armeabi-v7a, x86 and x86_64.
- Native bridge regression source covers ART attachment and nonblocking input
  polling while a simulated joystick mutex is held. WSL is unavailable on this
  run, so executing that pthread host test remains pending.
- No ADB device is connected. Rendering, real hotplug, audio codec operation and
  actual modded launches have not been validated on hardware.

Source for the Forge/OptiFine pairing: https://optifine.net/downloads
