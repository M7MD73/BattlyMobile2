# Game performance audit (2026-10-22)

## Confirmed and corrected

### Synchronous storage barriers on every log fragment

stdio_is.c previously called fdatasync after every write, including writes from
JNI callers. A noisy mod could fill the stdout pipe while its reader waited on
storage, blocking game logging threads and potentially the render/server thread.

Writes are still immediate and complete. Durability barriers are coalesced at
64 KiB or 250 ms on subsequent writes; descriptor replacement flushes the old
file. This does not buffer log content in application memory or remove crash
diagnostics. Only sudden device power loss can lose unflushed kernel-cache data;
ordinary process crashes do not discard that cache.

The descriptor and batching state now share a mutex. Partial writes/EINTR are
handled, and the stdout buffer is terminated before the existing token filter.

Android emulator measurements (Hytale x86_64 API35, same device/storage):

| Test | Before | After |
| --- | ---: | ---: |
| 2000 x 256-byte native writer calls | 4711.544 ms / 2001 barriers | 41.095 ms / 9 barriers |
| 2000 actual Logger.appendToLog JNI calls | 5008.7845 ms | 25.2244 ms |

Both tests verify every entry remains readable. These are I/O measurements, NOT
Minecraft FPS gains, and emulator storage is not representative of every phone.

Reproduction tools:
- tools/tests/log-file-writer-test.c (Android NDK executable).
- NativeLogIoDeviceTest (actual packaged JNI logger).
- Ignored build/native-log-before.txt and build/native-log-after.txt.

### Unchanged overlay widgets

Voice toggles previously rewrote text, colors, accessibility labels and invalidated
their view every 500 ms even with unchanged state. The FPS widget also rewrote
identical text. Both now skip unchanged values and invalidate their cached state
when edited, preserving correct editor behavior. The polling intervals remain
unchanged; this is a reduction in redundant UI work, not a major FPS claim.

## Investigated, not treated as proven universal causes

- Java startup forces SerialGC and a 48 MiB code cache when heap <= 1280 MiB,
  independent of device class. This is a candidate for pauses/code-cache pressure,
  but changing collectors can raise memory consumption on low-end devices. No
  default collector/heap/cache changes are made in this patch without matched
  gameplay/GC measurements on affected phones.
- GameHealthMonitor already samples on a background thread once per minute.
- Presence/usage heartbeats run at 20-second intervals with requests dispatched
  off the UI thread. Their existence alone does not prove an FPS bottleneck.
- Renderer/driver, shaders, resolution, chunk generation and thermal throttling
  require device-specific traces; no renderer defaults or visual settings are
  silently changed by this patch.

## Remaining validation

Verification completed: all 301 existing unit tests pass; NativeLogIoDeviceTest
and ControlWidgetRefreshDeviceTest pass on Hytale. The four-ABI debug APK builds.
Minecraft 1.21.11 reaches the title screen and loads the isolated existing world
with the updated native logger; the smoke screenshot is retained under ignored
build/performance-world-smoke.png. This short stationary session is not a matched
frame-time benchmark or a sustained-memory stability test.

Measure frame-time percentiles, GC pauses, thermal state and native/JVM memory
over sustained identical gameplay on affected physical devices. The emulator
and a successful build cannot establish a universal FPS improvement or prove
that every reported performance problem is resolved.
