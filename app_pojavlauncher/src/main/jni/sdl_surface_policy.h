#pragma once
#include <stdbool.h>

static inline bool battly_keep_visible_surface(bool enabled, bool target_hidden,
                                               bool current_visible, bool same_window) {
    return enabled && target_hidden && current_visible && !same_window;
}
