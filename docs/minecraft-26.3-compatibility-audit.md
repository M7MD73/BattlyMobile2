# Minecraft 26.3 compatibility audit (2026-10-22)

## Compared sources

- MojoLauncher/MojoLauncher: `7122869a3b533e08c18537908b44211c64409387`.
- ZalithLauncher/ZalithLauncher2: `3e2be4322fceac104fa93b4bcf91fa59fee42b8e`.
- MobileGL-Dev/MobileGlues dev: `0c27a3a8b3f51babd59fcca8ff59f28cbee1eecd`.
- MojoLauncher/LTW master: `2f4dbaa`.
- FCL-Team/NG-GL4ES: `472f55e549e2bc76c87454905db52661c3cf57a9`.

The MobileGlues release repository still lists V2.0.0 as its latest release.
Its selected development fixes were already incorporated on 2026-10-11.
Krypton has no newer source revision than the one already packaged. No Mesa,
ANGLE or Vulkan binary was replaced with an unverified third-party build.

## Applied corrections

### SDL hidden helper windows

Zalith's SDL integration explicitly handles Minecraft RenderPearl's hidden
helper window and Android's single visible native surface. Battly already has
distinct offscreen SDL windows, so copying Zalith's window-pointer aliasing and
reference-counting implementation would conflict with its lifetime model.

Instead, a Battly-owned linker wrapper prevents a hidden helper window from
replacing an already visible Android window when its context becomes current.
The helper retains its own offscreen surface and context. Promotion of a visible
window, initial hidden-window setup and the original destruction path are kept.
The integration is outside SDL's source tree and gated by
`BATTLY_SDL_STABLE_SURFACE=1`, set for the LWJGL 3.4.2 launch path only.
Older GLFW launch paths are unchanged.

### MobileGlues persistent mapping

When `buffer_coherent_as_flush` is enabled, storage already becomes coherent and
explicit flushes are omitted. The old map path removed the explicit-flush flag
without requesting coherent mapping. Persistent writes therefore did not carry
the visibility guarantee assumed by the skipped flush.

The mapping now requests coherence for persistent maps under that policy.
Nonpersistent maps and the disabled-policy path keep their prior behavior.
This addresses a concrete visibility defect, not a guarantee that every Sodium
flicker or native crash has the same cause.

### LTW upstream fixes

- `7285fad` plus its correction `9dc80cb`: retain shader input/output varyings
  during optimization so the stages can still link.
- `1c8472f`: detach temporary framebuffer texture attachments after copy/blit.

Experimental compute-shader support, incomplete clear-texture emulation and GL
version overrides from the newer LTW branch were deliberately not imported.
No renderer is advertised as supporting capabilities it does not implement.

## Verification and remaining checks

Host tests exercise all 16 hidden/visible surface-policy combinations and actual
MobileGlues mapping-policy code, including disabled mode, persistent writes,
nonpersistent reads/writes and idempotence. Android unit tests and native builds
are run separately; build success is not GPU-runtime proof.

No ADB device was connected during this audit. Before production, verify vanilla
26.3 and matching Sodium/Iris builds on Mali and Adreno: world rendering, moving
camera, inventory/chat, background/resume, resource reload, and normal shutdown.
Older Minecraft versions must also be smoke-tested on the same device.

The WSL host test suite could not start (STATUS_ACCESS_DENIED); the two new pure
policy tests were compiled and executed with the Windows LLVM toolchain instead.

Commands and observed results:

- `tools/tests/run-renderer-policy-tests.ps1`: both policies pass.
- `:app_pojavlauncher:testDebugUnitTest`: 293 tests, zero failures/errors.
- `:app_pojavlauncher:assembleDebug`: succeeds for all four packaged ABIs.
- Disassembly of `Android_GLES_MakeCurrent` confirms a call to
  `__wrap_Android_MakeWindowCurrent` in the built SDL library.

R8 settings, release version, account data and instance settings are unchanged.
