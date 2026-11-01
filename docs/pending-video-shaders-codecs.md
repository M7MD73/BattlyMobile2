# Video, log 88 and log 89 follow-up (2026-10-22)

## Window-size bridge

The video shows 26.2's Minecraft viewport changing geometry while Android's
controls retain their dimensions. The GLFW size bridge had two confirmed defects:
IntBuffer outputs advanced caller buffer positions (unlike LWJGL's native API),
and logical window sizes could remain stale after a native-surface resize.

Window/framebuffer queries now use absolute writes and accept null outputs.
Surface-following windows synchronize their dimensions; explicitly sized hidden
helper windows retain their own dimensions. Zero-sized transient surfaces are
ignored. Native callbacks and requested desktop-size protection remain in place.

The host probe executes these actual methods in all three packaged LWJGL
channels. Only Android/native class initialization is omitted. It checks repeated
queries, output positions, nullable buffers/arrays, native resize and a 1x1 hidden
helper. This is not visual replay of the original 146-mod installation.

## Log 88: poisoned shader program

The log's first shader error is not missing rendertype_text: it is entity_outline
containing Battly's legacy `targets/passes` workaround instead of a modern
`vertex/fragment` program. Resource reload subsequently fails, and the crash
screen itself cannot find its text shader.

Repair now derives the layout from Minecraft's actual version as well as the
archive marker. This covers OptiFine JARs that omit the vanilla post_effect marker.
Only the selected OptiFine library coordinates are additionally inspected, not
arbitrary mods or resource packs. The repair marker is bumped to fix6. An archive
without a needed shader change remains byte-for-byte untouched, including its
signature entries. Actual changes retain the pre-existing atomic rename path.

The host archive probe executes the real repair class with only Android rename
and native logging replaced. It checks a poisoned program without the marker,
preservation of unrelated resources, idempotence and a custom signed JAR that
must remain byte-identical.

## Log 89: separate AWT and codec failures

The original attribution to MoreCosmetics for all native codec failures was
incorrect. The codec traces come from Simple Voice Chat 1.21.11-2.6.11. The
MoreCosmetics 1.3 JAR does not bundle those codecs; its `log` method references a
DebugConsole extending JFrame, causing AWT initialization even without opening
the console.

Battly removed the earlier headless JVM option and appended the replacement after
Minecraft's main class. Minecraft explicitly logged it as an ignored application
argument. The override is now before the main class; unrelated application
arguments and installer behavior are preserved. No AWT native method is stubbed
or exception swallowed. The unmodified MoreCosmetics 1.3 DebugConsole.print path
passes a host headless initialization probe. A full modded Android launch is
still a device-validation requirement.

### Android codec ports

Four actual JNI libraries are compiled against Android bionic for arm64-v8a,
armeabi-v7a, x86_64 and x86. They are not renamed glibc libraries. Only JNI exports
are public, avoiding symbol interposition with Battly Worlds/WebRTC. Licenses are
packaged in android-audio-codecs.aar; upstream sources and dependency revisions
are recorded in tools/build-android-codecs.ps1 and upstream CMakeLists.txt files.
The AAR contains no replacement Java codec classes.

| Wrapper | Source commit | Reference Java artifact |
| --- | --- | --- |
| opus4j | 362db14553d39c4dc080e4edd50630b17dbf9682 | 2.1.3 |
| rnnoise4j | 5f2097d504fc67f31d0d8900e9df0a5bdf4ec9ae | 2.1.3 |
| speex4j | 9428cb14b8ec373fcd36f5b5792899611042ece9 | 1.0.3 |
| lame4j | b09599989878cbc7349075eca0b3aa4f105e5957 | 2.1.5 |

Codec paths are activated only for instances using those wrappers/Voice Chat.
Before redirecting System.load, the patch agent checks every native class's exact
method signatures, static/instance calling convention and ELF architecture.
System.load remains in the mod's own class loader. Unknown JNI APIs, missing
files and mismatched architectures keep their original loader behavior.

Contracts pass against the actual unmodified Simple Voice Chat 2.6.11 JAR from
Modrinth, including rejection tests for altered APIs, missing classes and wrong
ABIs. All 16 Android binaries contain no glibc SONAME dependencies. Runtime
encoding, decoding, denoising and microphone operation on Android remain
unverified without a connected device.

## Reproduce

```powershell
./tools/build-android-codecs.ps1
./tools/build-lwjgl-patch-agent.ps1
./gradlew.bat :app_pojavlauncher:testDebugUnitTest :app_pojavlauncher:assembleDebug
./tools/tests/run-pending-compatibility-probes.ps1
```

The host probes expect official, unmodified fixtures in ignored build storage:
MoreCosmetics Modrinth version GSHsu4pK (1.3 for 1.21.11), and Simple Voice Chat
1.21.11-2.6.11 Fabric. Downloaded files were checked against Modrinth SHA-512.
Do not redistribute the mod fixtures as launcher assets.

Native-contract properties in jre_lwjgl3glfw/agent-resources are generated by
tools/GenerateCodecContract.java from official Maven artifacts. Their SHA-256:

- opus4j: 49747D0E066EA65201971384822ECDC6EAF84B3AE848F2BE5F347DF9CCD82024
- rnnoise4j: 08146EB9FF046C13A82022315D209535361DEB271D4359D445FD149378B8AB88
- speex4j: 65BAC907C515B4D78A7C1DD60CF009AC82982F9E1A040E756DBF4DAB5C81454F
- lame4j: 886C3EC9F352200EB017E3EE8F34A3BF435E3D9F0252531303E9F402B465905C

## Remaining validation

Observed: 301 Android unit tests, zero failures/errors; all host probes pass;
debug APK builds for four ABIs; packaged ELF page-size check reports zero
failures. These checks do not perform an Android codec round-trip.

No ADB device was connected. Confirm the original 26.2 modpack/control layout,
the 1.21.4 OptiFine preview and MoreCosmetics/Voice Chat together on actual
Android hardware. Host probes and a successful APK build are not proof of
complete visual, audio or multiplayer behavior. R8 and release version settings
were not changed; no user instance or game configuration was deleted.
