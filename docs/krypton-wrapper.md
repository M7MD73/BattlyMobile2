# Krypton Wrapper

Battly bundles NG-GL4ES (Krypton Wrapper 0.4.5) as an additional manual renderer.
Holy GL4ES remains installed and existing profiles keep their renderer selection.

Source: https://github.com/FCL-Team/NG-GL4ES
Pinned revision: `472f55e549e2bc76c87454905db52661c3cf57a9`.
Upstream: https://github.com/BZLZHH/NG-GL4ES

Rebuild the native AAR on Windows with `tools/build-krypton.ps1`. It uses NDK
27.3.13750724, CMake 3.31.6, the pinned recursive submodules, static libc++, and
16 KB page alignment. Source and intermediate builds stay under ignored `build/`.
The distributable is `app_pojavlauncher/libs/krypton-wrapper-0.4.5.aar` and
contains arm64-v8a, armeabi-v7a, x86_64 and x86, plus third-party license notices.

The AAR does not raise Battly's minimum Android version: the renderer registry
requires Android 8 (API 26), GLES 3 and an installed library for the running ABI
before offering Krypton. Devices that cannot load it keep the existing renderers.
Context creation uses Android EGL; Krypton is the OpenGL library, not an EGL
replacement. Do not point POJAVEXEC_EGL at libng_gl4es.so.

Open4ES variants have different requirements. Providing this renderer does not
guarantee that every shader pack or Minecraft version works on every GPU.

## OptiFine shader safety patch

`tools/krypton/patches/0001-optifine-shader-safety.patch` is applied by the build
script to the pinned upstream revision. Keep it when rebuilding the AAR.

- `glBindFragDataLocation` previously copied a longer, location-qualified shader
  into its original smaller allocation. This caused the reported native SIGSEGV
  in `__strcpy_aarch64` from OptiFine's `Shaders.setupProgram`. The replacement
  allocation now becomes the owned source, and the search pointer is rebased.
- The original shader backup is a separate allocation, with cleanup on source
  replacement/deletion. The compatibility conversion no longer reads or reuses
  the source after freeing it.
- Desktop translation uses OpenGL semantics consistently and normalizes older
  desktop GLSL to the OpenGL 4.50 SPIR-V target before conversion to GLES. This
  covers Forge's GLSL 150 splash and line continuations in Complementary's GLSL
  400 source. Native GLES sources keep their direct path.
- Translation failures are logged instead of being hidden in debug-only output.

`tools/krypton/shader-smoke.c` runs against the actual Android library and driver
using an EGL pbuffer. It repeats source replacement, compilation, fragment output
binding, linking and deletion 100 times per fixture (GLES 300, desktop GLSL 150,
and GLSL 400 with a continued comment). An optional second argument compiles a
captured vertex shader; captured shader packs and logs must stay out of Git.

Example for arm64 (substitute the device serial):

```powershell
& "$env:ANDROID_HOME/ndk/27.3.13750724/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" --target=aarch64-linux-android26 tools/krypton/shader-smoke.c -o build/krypton-shader-smoke -lEGL -ldl -Wall -Wextra
adb -s SERIAL push build/krypton-shader-smoke build/krypton-native/arm64-v8a/libng_gl4es.so /data/local/tmp/
adb -s SERIAL shell 'chmod 755 /data/local/tmp/krypton-shader-smoke; NGG_DIR_PATH=/data/local/tmp /data/local/tmp/krypton-shader-smoke /data/local/tmp/libng_gl4es.so'
```

# Mouse side buttons

Physical BACK/FORWARD buttons map to Minecraft mouse 4/5. Android key fallback
events are deduplicated with motion events; BTN_RIGHT scan code 273 retains right
click. Focus loss releases held buttons. Bind actions in Minecraft's Controls.
The SDL bridge compensates for SDL Android's X1/X2 mapping, preserving the same
button numbering across GLFW and SDL versions.

The bundled LWJGLX declared eight buttons but allocated only three bytes for
their states. Its constructor now allocates at least eight, including the copy
inside `lwjgl-glfw-classes.jar`; otherwise mouse 4/5 throw inside the JVM callback.
`LegacyMouseButtonsTest` loads each packaged implementation and presses/releases
every advertised button. Component version r21 deploys the repaired JARs to
existing installations without touching worlds or user controls.

To reapply the bytecode repair after replacing an upstream LWJGLX JAR (JDK 17+):

```powershell
javac -cp app_pojavlauncher/src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar -d build/mouse-patcher tools/lwjgl/PatchMouseButtons.java
java -cp "build/mouse-patcher;app_pojavlauncher/src/main/assets/components/lwjgl3/lwjgl-patch-agent.jar" PatchMouseButtons jre_lwjgl3glfw/libs/lwjgl-lwjglx.jar app_pojavlauncher/src/main/assets/components/lwjgl3/3.3.3/lwjgl-lwjglx.jar app_pojavlauncher/src/main/assets/components/lwjgl3/3.4.1/lwjgl-lwjglx.jar app_pojavlauncher/src/main/assets/components/lwjgl3/lwjgl-glfw-classes.jar
```

The patch retains the original Java bytecode target and is idempotent.

## Verification (2026-09-23)

- Debug APK builds; 280 JVM tests pass.
- Android API 35 x86_64 emulator: Minecraft 1.7.10 starts with Krypton and
  renders its menu and a local world.
- Mouse-source BACK/FORWARD injected through Android are assignable as Button
  4/5 in Minecraft Controls. They open inventory/chat in the test world, with
  no LWJGLX mouse callback exceptions after the repair.
- The emulator's original profile was restored and the isolated test world
  removed. No physical mouse, Adreno/Mali phone, SDL Minecraft session or
  Open4ES shader pack was verified in this run; no cross-GPU performance claim.

### ForgeOptiFine follow-up (2026-09-23)

- Rebuilt the patched native library for all four ABIs; debug APK and all 280
  JVM tests pass. Patch application checked against a clean upstream index and
  an already-patched checkout.
- S26 / Xclipse 960: the initial memory-safety patch passed 200 native shader
  cycles and reached a Forge 47.4.23 + OptiFine I6 world without the reported
  native crash. Complementary r5.9.3 still failed GLSL translation at this stage.
- Captured that failing shader locally and reproduced the GLSL 400 continued
  comment failure. With desktop normalization, it compiles on Android.
- Final x86_64 API 35 emulator: 300 native shader cycles pass. Forge 47.4.23,
  OptiFine I6 and unmodified Complementary Reimagined r5.9.3 load all 27 Overworld
  shader programs without compilation/link errors; an actual world renders with
  textures, lighting and shadows, and accepts movement and a time-change command.
  The world saved normally; the emulator profile was restored and the isolated
  test directory removed.
- Remaining caveats: the emulator logs unavailable-function warnings during
  initial shader setup. Other dimensions, packs and GPUs are not verified. The
  S26 disconnected before the final translation fix could be installed/tested;
  do not present the emulator result as final Xclipse/Adreno/Mali validation.
