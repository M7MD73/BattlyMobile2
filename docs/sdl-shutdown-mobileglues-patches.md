# SDL shutdown and MobileGlues patches (2026-10-11)

The Samsung SM-A145M log reports Minecraft 26.3 stopping normally, followed by
a native SIGSEGV in `libSDL3.so`, `SDL_IsDeXMode+0x24`. The function calls Android
JNI directly from Minecraft's embedded HotSpot thread. This is the same unsafe
cross-VM path already isolated for relative-mouse queries in Battly's SDL build.

Battly's build now wraps SDL's internal DeX and Chromebook queries. Each query
attaches a fresh pthread to Android's VM before calling the original SDL
implementation and joins it before returning. SDL detaches the worker through
its existing thread-local destructor. Results remain live, including DeX mode
changes. If thread creation or Android attachment fails, the query returns false.
The wrapper is integrated outside the upstream SDL tree.

The linker redirects SDL's internal references. Public SDL symbols and LWJGL's
ABI remain unchanged. No game-exit error is suppressed by this correction.

## MobileGlues upstream changes

Applied to the existing local 2.0.0 tree, preserving Battly's other corrections:

- `0f1e10b53b2d5d0c474dbe1af3554188b52ae51d`: retain indirect-command staging
  storage after smaller draws (main branch).
- `7bb2325befc47a687305374b839b49ba4f82a47b`: token-based uniform initializer
  processing and restoration of default uniform values after program linking.
  Includes upstream regression tests. The GLSL cache key is changed so prior
  stripped translations are not reused (development branch).
- `fcdf914`: retain renderer/version strings from the bootstrap context and
  avoid dereferencing null strings when no backend context is current
  (development branch). Battly's GLES 3.0 fallback is preserved.
- `18b34b07966bf25d62135549f5fd272ad21b8680`: advertise
  `GL_EXT_clip_cull_distance` only when the backend supports it (development
  branch).

This is 2.0.0 with selected upstream patches, not a claim that 2.0.1 is released.

## Verification

Host checks link the actual Battly wrapper and MobileGlues source files:

```sh
sh tools/tests/run-sdl-android-device-bridge.sh
sh MobileGlues/MobileGlues-cpp/tests/run.sh
```

The SDL check verifies worker-thread isolation, changing device-query results,
repeated calls, and both thread-creation and Android-attachment failures.
The MobileGlues checks cover pixel sizes, framebuffer attachment shuffling, and
uniform declarations/default values. They do not prove GPU rendering on Mali.

```powershell
.\gradlew.bat :app_pojavlauncher:testDebugUnitTest :app_pojavlauncher:assembleDebug -PbattlyDebugAbi=arm64-v8a --console=plain
```

Device verification must include playing and then closing Minecraft 26.3 on a
Samsung/Mali device. At patch preparation there was no Android device connected
to ADB, and this host's hardware virtualization was unavailable.
