# Physical Peripheral Input

Entry point: Settings > Controls > Keyboard, mouse and controller.

## Compatibility Contract

- Existing mouse resolution scaling remains the default. The PC preset opts into a constant linear gain independent of framebuffer scale. X/Y sensitivities and inversion are separate from touchscreen and menu-pointer sensitivity.
- Battly does not add mouse smoothing, velocity-based acceleration, integer rounding or deferred motion. This is Android pointer capture, not a claim of access to raw USB HID data or control over vendor/Android acceleration.
- Pointer capture and calibration require Android 8+. Historical captured samples are relative deltas: sum all samples exactly once before forwarding. Coordinates and alternative relative axes are not added together.
- Keyboard bindings are one-to-one gameplay mappings, not commands/macros, auto-clickers, repeat generators or arbitrary text execution. Modifier/system keys retain their normal path. Menus and chat do not start new remapped holds.
- A held mapping remains pinned until release, even if preferences or grab state change. Ownership is counted by emitted GLFW key, not by Android aliases. Focus loss, view detachment and keyboard disconnection release mapped holds.
- Gamepad linear response is optional; quadratic response remains the default. This affects Battly's gamepad emulation, not native controller mods. Existing deadzone calibration, sensitivity, inversion and setup remain available.
- Diagnostics are local only. No key names, typed text or motion samples are sent to analytics or saved by the diagnostic view. Measurements are reported Android motion, not DPI or a 360-degree sensitivity measurement.

## Verification

- Unit tests: linear gain, fractional motion, render-scale independence, legacy scaling, invalid input, stick response, aliases/shared holds, focus cleanup and device cleanup.
- Android instrumentation: batched MotionEvent samples and relative-axis fallback; physical-key event routing using the emulator's keyboard device; native preferences and the PC preset.
- Emulator UI rendering is not evidence of end-to-end Minecraft 1.8.9 BedWars performance, real USB/Bluetooth compatibility or measured input latency. Those still require an actual gameplay/peripheral session.
- New strings are included in the Android localization audit manifest.

## Sources

- Android pointer capture: https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement#pointer-capture
- Android MotionEvent history: https://developer.android.com/reference/android/view/MotionEvent
