# Keyboard input fixes (2026-10-22)

## Changes

- TouchCharInput no longer disables itself on window-focus gain; it is focusable
  in touch mode. Opening is idempotent and retries IME activation without toggling
  a newly focused editor off.
- Automatic GLFW notifications use the classic editor for classic input rather
  than attempting to open the SDL editor without an SDL host.
- SDL deletion mirrors changes into its Editable/mCommittedText, supports
  deleteSurroundingTextInCodePoints and KEYCODE_DEL, and still forwards backspace
  when the game contains text absent from the local IME buffer.
- Soft DEL events are no longer forwarded to the inactive classic editor when
  using SDL; held/repeated DEL events are accepted.
- Atomic request generations reject stale show/hide/retry operations. A queued
  old hide cannot close the input just selected by the user. restartInput is not
  repeated for an already served editor, preserving composing text.
- Controls settings contains autoOpenGameKeyboard (default true). Automatic GAME
  requests respect it; manual LAUNCHER requests remain available. The title is
  present in all 51 existing localized strings files.

## Verification

- Seven Android instrumentation tests pass on Hytale x86_64 API35: SDL text
  deletion/model consistency, Unicode deletion, DEL pairs, empty-buffer deletion,
  classic focus/deletion, automatic/manual preference separation and stale-hide
  ordering. SDL connection tests replace only the native emission boundary.
- Minecraft 1.21.11: opened a world with the isolated local QA account, opened
  Gboard manually, typed abc using its visible keys, deleted c and dismissed the
  IME. Minecraft visibly retained ab. Screenshots are in ignored build/:
  keyboard-visible.png and keyboard-game-backspace.png.
- The game was saved and the original account/profile selection restored.
- The four-ABI debug build and 301 existing unit tests pass.

Automatic focus-detection coverage itself is unchanged: the agent currently
hooks the named EditBox/MultiLineEditBox classes, while SDL supplies its own text
input lifecycle. This patch does not add obfuscated Minecraft-class mappings.
The 1.21.11 gameplay check above verifies manual input/deletion, not automatic
opening on every historical Minecraft/modded text widget or every vendor IME.
