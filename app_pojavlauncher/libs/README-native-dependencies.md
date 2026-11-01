# Bundled Android native dependencies

These AARs provide Android builds for native libraries that some Minecraft mods
otherwise load from desktop Linux artifacts.

The dependency catalog can also extract matching Java classpath artifacts when
a future mod requires classes that are not already shaded into its own JAR.
The dependencies listed below currently need only their ABI-specific native
libraries, so no extra classpath artifact is bundled for them.

| File | Upstream | Source revision | License |
| --- | --- | --- | --- |
| `zstd-jni-dhcompat-1.5.7-6.aar` | `ZalithLauncher/NativeLibPlugin`, branch `plugin/zstd-jni-dhcompat` | `e267b3c` | zstd GPLv2 or BSD; zstd-jni BSD-2-Clause |
| `imgui-java-1.86.12-patched.aar` | `ZalithLauncher/NativeLibPlugin`, branch `plugin/imgui-java` | `ea476cf` | MIT |
| `imgui-moulberry92-1.92-moulberry.2.aar` | `Moulberry/imgui-java`, tag `v1.92-moulberry.2` | `4a2813430cc2bc64ba2106289d0c8606e656f32d` | MIT (vendored notices included) |
| `sqlite-jdbc-3.53.2.1-dh-android.aar` | `xerial/sqlite-jdbc`, rebuilt for Android with Distant Horizons' relocated `dh_sqlite` JNI namespace | `3.53.2.1` (`94c0ea1`) | Apache-2.0 |

The upstream license texts are retained inside each AAR under
`assets/licenses/` and are merged into the application assets by Gradle.

Recorded SHA-256 checksums:

- `zstd-jni-dhcompat-1.5.7-6.aar`: `B08DAB9DDFC522BAD765BF99266CCD2E1B4C9492599AF78C795C316A11B7051D`
- `imgui-java-1.86.12-patched.aar`: `A50182FE4F12202AC95D8E6C156ACDB09F18840C6E4892775DBA56D801ADFAFA`
- `imgui-moulberry92-1.92-moulberry.2.aar`: `751A3D1E6E6BE754DE7CC93E2D02A577AF1BCBE8595B8E75E27E1A77F72A9A72`
- `sqlite-jdbc-3.53.2.1-dh-android.aar`: `F06BC1E0F7B776986CD0AAA10A4064216E527DA3913B0E58CA6ADC3D630F3C8E`

The Distant Horizons SQLite build changes the JNI exports and callback class
paths from `org.sqlite` to the mod's `dh_sqlite` relocation. It is compiled for
Android API 24 with NDK 28.2 and 16 KB ELF page alignment. The SQLite
amalgamation and Java/native bridge remain at upstream version 3.53.2.1.

The relocated `imgui.moulberry92` binding in Axiom 6.0.5 is a separate JNI API,
not an alias for the older `imgui-java` binary. Build it with
`tools/build-imgui-moulberry92.ps1` (Android API 21, four ABIs, static C++ runtime,
16 KB ELF page alignment). `tools/imgui/android-jni.gradle` generates the JNI
bridge from the pinned upstream Java sources, and CMake compiles those sources
with the matching Dear ImGui and extension revisions. The upstream node-editor
compatibility patch is applied unchanged. JNI environment discovery requests
JNI 1.6, which both Android ART and the embedded HotSpot runtime support.

Battly's agent replaces only `imgui.moulberry92.ImGui.tryLoadFromClasspath`,
and only for `imgui.java.version=1.92-moulberry.2` with a matching ELF architecture.
The mod's own initializer still calls `System.load` and initializes JNI in its
own class loader. It no longer extracts a desktop binary over its cache file.
Unknown versions and ordinary ImGui classes are not rewritten. The original
mod JAR and existing user files are never modified or deleted.

`tools/imgui/ImGuiSmoke.java` exercises the original Axiom JAR on Android:
JNI initialization, context creation, a font atlas and 120 frames of draw data.
It does not by itself prove a complete Minecraft session or GPU rendering.

Validation on 2026-09-25: Axiom 6.0.5 (unmodified Modrinth artifact, SHA-1
`8fbab015a84d46a1a750e64f7142a11bcb431a0f`) with Fabric 0.19.2 / Minecraft
1.21.11 / MobileGlues launched on the x86_64 API 35 emulator. A creative world
loaded and Axiom's editor panels rendered. That run also exposed the missing
GLFW window-content-scale overload; both array and FloatBuffer overloads are
now packaged in all three supported LWJGL channels. The standalone Android
smoke test also exercises the script text editor (120 frames, 46,874 vertices).
All four native ELF architectures and 16 KB alignment were checked; physical
ARM64 / Adreno device rendering remains unverified.
# Android Audio Codecs

`android-audio-codecs.aar` supplies Android/bionic JNI builds of opus4j, rnnoise4j,
speex4j and lame4j for all four supported ABIs. It contains no desktop glibc
libraries and no replacement Java classes. The patch agent requires an exact
JNI contract and matching ELF architecture before changing a mod's load path.
Paths are enabled only for instances containing these wrappers or Voice Chat.

Build: `tools/build-android-codecs.ps1`. Source pins, licenses, reference artifact
hashes and verification limits: `docs/pending-video-shaders-codecs.md`.

