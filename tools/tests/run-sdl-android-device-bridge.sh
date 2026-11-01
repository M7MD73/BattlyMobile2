#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
output_dir="$(mktemp -d)"
trap 'rm -f "$output_dir/sdl_device_bridge_test"; rmdir "$output_dir"' EXIT
${CC:-cc} -std=c11 -Wall -Wextra -Werror -pthread \
    app_pojavlauncher/src/main/jni/sdl_android_device_bridge.c \
    tools/tests/sdl_android_device_bridge_test.c \
    tools/tests/sdl_android_device_bridge_calls.c \
    -Wl,--wrap=SDL_IsDeXMode -Wl,--wrap=SDL_IsChromebook \
    -Wl,--wrap=Android_JNI_PollInputDevices \
    -Wl,--wrap=pthread_create -o "$output_dir/sdl_device_bridge_test"
"$output_dir/sdl_device_bridge_test"
