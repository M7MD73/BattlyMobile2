# FPS limits after EGL surface replacement

The classic GL bridge did not remember Minecraft's swap interval when replacing
an EGLSurface. EGL initializes replacement surfaces with interval 1; a game that
already disabled VSync does not necessarily send that setting again. SDL's Android
surface replacement also rebound the context without restoring its cached interval.

Both paths now restore the game interval after successful rebinding, retaining
explicit launcher-forced VSync. A failed SDL interval request is logged without
turning a successful context bind into a fatal error.

`tools/tests/swap-interval-test.c` includes the actual classic bridge and mocks EGL.
It simulates replacement resetting the interval to 1 and checks disabled VSync,
enabled VSync, forced VSync, and context rebinding. Executed on emulator-5554:
PASS, 8 interval applications. This is a native regression test, not a physical
device FPS measurement; SDL is compiled but not covered by that mocked test.

Other independent limits remain intentional or device-dependent:

- Minecraft's own maxFps setting, including the 60 FPS default for allocations
  of at most 1280 MB when no maxFps value exists. Existing user settings are preserved.
- Explicit force_vsync and vsync_in_zink preferences.
- TextureView composition, driver swap-interval constraints, Android power/game
  policies and thermal limits. A display rate request is not an FPS unlock.

Launch logs now include surface type, requested display rate and launcher/game
VSync and maxFps settings. No surface compatibility rules or saved options were
changed to bypass those independent constraints.
