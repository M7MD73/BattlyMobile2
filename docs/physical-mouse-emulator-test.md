# Physical mouse emulator regression (2026-10-22)

## Setup

- Hytale_x86_64_API35, Android 15, emulator-5554.
- Updated debug APK; Minecraft 1.21.11, MobileGlues, Java 21.
- Separate local account BattlyMouseQA and isolated mouse-qa-world directory.
- No device-link consent accepted for the existing Battly account.
- tools/tests/android-mouse-probe.c creates a Linux uinput USB mouse (requires
  adb root on a test emulator). Android InputReader reports MOUSE, external,
  ControllerNum 0. Events pass through the Android input stack, not a mocked sink.

## Reproduced issue and correction

The original patched APK still failed to capture the pointer on world entry:
Minecraft logged Grab changed: true, but dumpsys input reported Pointer Capture
Disabled, seq=0. MinecraftGLSurface was focusable but not focusable in touch mode.
After a touchscreen launch, requesting focus could therefore fail.

MinecraftGLSurface now permits focus in touch mode. Incoming mouse motion also
retries automatic capture while Minecraft is grabbing. Menus retain their normal
release behavior. This is in addition to the mouse/controller source separation.

## Observed results after correction

- World entry: Pointer Capture Enabled, seq=1, MainActivity owns capture.
- Horizontal/vertical uinput motion: Minecraft F3 orientation changes (13.5, 9.0
  after the combined-axis movement); world position stays 5.5, 90.0, -1.5.
- Middle button selects birch leaves into the first hotbar slot.
- Wheel down selects slot two; wheel up returns to slot one.
- Left button breaks the leaf block; right button places it back. Screenshots
  show the open view and restored leaves respectively.
- ESC releases capture (Disabled, seq=2). Returning to the game restores it
  (Enabled, seq=3); subsequent motion remains functional.
- Save and Quit finishes saving all dimensions successfully.

Generated screenshots are kept under ignored build/:
mouse-reopen-world.png, mouse-after-both-axes.png, mouse-wheel-next.png,
mouse-left-break.png, mouse-right-place.png, mouse-recapture.png.

The opt-in MouseWorldFixtureTest instrumentation fixture preserves/restores the
previous account/profile selection. The created world remains in its isolated
test directory for reproduction, not in any existing instance.

## Boundaries

This verifies one real Minecraft session using an emulated USB mouse and the
GLFW input path. It does not prove every USB/Bluetooth driver or the SDL input
path used by newer Minecraft versions. Mixed mouse/controller event sources are
separately covered by PhysicalMouseInputDeviceTest.
