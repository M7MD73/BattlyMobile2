#include <assert.h>
#include <stdio.h>
#include "../../app_pojavlauncher/src/main/jni/sdl_surface_policy.h"

int main(void) {
    for (int enabled = 0; enabled < 2; enabled++)
        for (int hidden = 0; hidden < 2; hidden++)
            for (int visible = 0; visible < 2; visible++)
                for (int same = 0; same < 2; same++)
                    assert(battly_keep_visible_surface(enabled, hidden, visible, same)
                           == (enabled && hidden && visible && !same));
    puts("SDL surface policy: all 16 cases passed");
    return 0;
}
