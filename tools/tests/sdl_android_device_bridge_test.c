#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdatomic.h>
#include <sched.h>

extern bool SDL_IsDeXMode(void);
extern bool SDL_IsChromebook(void);
extern int __real_pthread_create(pthread_t *, const pthread_attr_t *,
                                  void *(*)(void *), void *);

static pthread_t minecraft_thread;
static _Thread_local bool art_attached;
static bool attach_succeeds = true;
static bool create_succeeds = true;
static bool dex;
static bool chromebook;
static unsigned queries;
static atomic_uint polls;
static pthread_mutex_t joystick_lock = PTHREAD_MUTEX_INITIALIZER;
extern void verify_sdl_input_poll(void);

int __wrap_pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                         void *(*entry)(void *), void *data) {
    return create_succeeds ? __real_pthread_create(thread, attr, entry, data) : EAGAIN;
}

bool Android_JNI_SetupThread(void) {
    assert(!pthread_equal(pthread_self(), minecraft_thread));
    art_attached = attach_succeeds;
    return art_attached;
}

bool SDL_IsDeXMode(void) {
    assert(art_attached);
    assert(!pthread_equal(pthread_self(), minecraft_thread));
    ++queries;
    return dex;
}

bool SDL_IsChromebook(void) {
    assert(art_attached);
    assert(!pthread_equal(pthread_self(), minecraft_thread));
    ++queries;
    return chromebook;
}

void Android_JNI_PollInputDevices(void) {
    assert(art_attached);
    assert(!pthread_equal(pthread_self(), minecraft_thread));
    pthread_mutex_lock(&joystick_lock);
    atomic_fetch_add(&polls, 1);
    pthread_mutex_unlock(&joystick_lock);
}

// Called by the separate translation unit so the test exercises linker wrapping.
extern void verify_sdl_device_queries(bool, bool);

int main(void) {
    minecraft_thread = pthread_self();
    for (unsigned i = 0; i < 64; ++i) {
        dex = (i & 1) != 0;
        chromebook = (i & 2) != 0;
        verify_sdl_device_queries(dex, chromebook);
    }
    assert(queries == 128);
    attach_succeeds = false;
    verify_sdl_device_queries(false, false);
    assert(queries == 128);
    attach_succeeds = true;
    create_succeeds = false;
    verify_sdl_device_queries(false, false);
    assert(queries == 128);
    create_succeeds = true;
    pthread_mutex_lock(&joystick_lock);
    verify_sdl_input_poll();
    pthread_mutex_unlock(&joystick_lock);
    while (atomic_load(&polls) == 0) sched_yield();
    puts("PASS: SDL device queries run outside the Minecraft thread; live results and failure paths verified");
    return 0;
}
