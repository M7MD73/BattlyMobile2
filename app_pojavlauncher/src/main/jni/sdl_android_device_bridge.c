#include <stdbool.h>
#include <pthread.h>
#include <stdatomic.h>

extern bool Android_JNI_SetupThread(void);
extern bool __real_SDL_IsDeXMode(void);
extern bool __real_SDL_IsChromebook(void);
extern void __real_Android_JNI_PollInputDevices(void);

bool __wrap_SDL_IsDeXMode(void);
bool __wrap_SDL_IsChromebook(void);
void __wrap_Android_JNI_PollInputDevices(void);

typedef struct {
    bool (*query)(void);
    bool result;
} BattlyAndroidDeviceQuery;

static void *run_android_device_query(void *userdata) {
    BattlyAndroidDeviceQuery *call = userdata;
    if (Android_JNI_SetupThread()) {
        call->result = call->query();
    }
    return NULL;
}

static bool android_device_query(bool (*query)(void)) {
    // ART JNI calls must not use a thread owned by the embedded HotSpot VM.
    BattlyAndroidDeviceQuery call = { query, false };
    pthread_t thread;
    if (pthread_create(&thread, NULL, run_android_device_query, &call) != 0) {
        return false;
    }
    pthread_join(thread, NULL);
    return call.result;
}

bool __wrap_SDL_IsDeXMode(void) {
    return android_device_query(__real_SDL_IsDeXMode);
}

bool __wrap_SDL_IsChromebook(void) {
    return android_device_query(__real_SDL_IsChromebook);
}

static atomic_flag polling_inputs = ATOMIC_FLAG_INIT;

static void *poll_input_devices(void *unused) {
    (void)unused;
    // SDL may hold its joystick lock on the caller. Do not join this worker:
    // Java reports hotplug changes back to SDL and needs that same lock.
    pthread_detach(pthread_self());
    if (Android_JNI_SetupThread()) __real_Android_JNI_PollInputDevices();
    atomic_flag_clear(&polling_inputs);
    return NULL;
}

void __wrap_Android_JNI_PollInputDevices(void) {
    if (atomic_flag_test_and_set(&polling_inputs)) return;
    pthread_t thread;
    if (pthread_create(&thread, NULL, poll_input_devices, NULL) != 0) {
        atomic_flag_clear(&polling_inputs);
    }
}
