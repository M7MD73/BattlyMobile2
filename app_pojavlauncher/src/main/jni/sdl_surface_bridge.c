// Battly integration kept outside the upstream SDL tree.
#include "SDL_internal.h"
#include "video/android/SDL_androidwindow.h"
#include "sdl_surface_policy.h"

extern void __real_Android_MakeWindowCurrent(SDL_VideoDevice *device, SDL_Window *window);
void __wrap_Android_MakeWindowCurrent(SDL_VideoDevice *device, SDL_Window *window);

void __wrap_Android_MakeWindowCurrent(SDL_VideoDevice *device, SDL_Window *window) {
    const char *enabled = SDL_getenv("BATTLY_SDL_STABLE_SURFACE");
    // RenderPearl/Sodium may use a hidden helper context after showing the game.
    // Keep that window on its pbuffer instead of repeatedly destroying the
    // visible Android EGL surface whenever the helper context becomes current.
    if (window && Android_Window && battly_keep_visible_surface(
            enabled && SDL_strcmp(enabled, "1") == 0,
            (window->flags & SDL_WINDOW_HIDDEN) != 0,
            (Android_Window->flags & SDL_WINDOW_HIDDEN) == 0,
            window == Android_Window)) {
        return;
    }
    __real_Android_MakeWindowCurrent(device, window);
}
