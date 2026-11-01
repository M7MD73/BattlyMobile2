#include <assert.h>
#include <stdbool.h>

extern bool SDL_IsDeXMode(void);
extern bool SDL_IsChromebook(void);
extern void Android_JNI_PollInputDevices(void);

void verify_sdl_input_poll(void) {
    Android_JNI_PollInputDevices();
}

void verify_sdl_device_queries(bool dex, bool chromebook) {
    assert(SDL_IsDeXMode() == dex);
    assert(SDL_IsChromebook() == chromebook);
}
