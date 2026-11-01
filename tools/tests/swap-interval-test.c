#include <assert.h>
#include <stdio.h>
#include "../../app_pojavlauncher/src/main/jni/ctxbridges/gl_bridge.c"

static int applied = -1;
static int applications;
static EGLBoolean mock_interval(EGLDisplay display, EGLint interval) {
    applied = interval;
    applications++;
    return EGL_TRUE;
}
static EGLBoolean mock_current(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context) {
    applied = 1; // A newly bound replacement surface has EGL's default.
    return EGL_TRUE;
}
static EGLBoolean mock_destroy(EGLDisplay display, EGLSurface surface) { return EGL_TRUE; }
static EGLSurface mock_pbuffer(EGLDisplay display, EGLConfig config, const EGLint *attributes) {
    return (EGLSurface) 1;
}
static EGLSurface mock_window(EGLDisplay display, EGLConfig config, NativeWindowType window,
                             const EGLint *attributes) { return (EGLSurface) 1; }
static EGLBoolean mock_swap(EGLDisplay display, EGLSurface surface) { return EGL_TRUE; }
static EGLint mock_error(void) { return EGL_SUCCESS; }

struct pojav_environ_s *pojav_environ;
EGLBoolean (*eglMakeCurrent_p)(EGLDisplay, EGLSurface, EGLSurface, EGLContext) = mock_current;
EGLBoolean (*eglDestroySurface_p)(EGLDisplay, EGLSurface) = mock_destroy;
EGLSurface (*eglCreatePbufferSurface_p)(EGLDisplay, EGLConfig, const EGLint *) = mock_pbuffer;
EGLSurface (*eglCreateWindowSurface_p)(EGLDisplay, EGLConfig, NativeWindowType, const EGLint *) = mock_window;
EGLBoolean (*eglSwapBuffers_p)(EGLDisplay, EGLSurface) = mock_swap;
EGLBoolean (*eglSwapInterval_p)(EGLDisplay, EGLint) = mock_interval;
EGLint (*eglGetError_p)(void) = mock_error;

int main(void) {
    struct pojav_environ_s environment = {0};
    pojav_environ = &environment;
    gl_render_window_t bundle = {0};
    bundle.surface = (EGLSurface) 1;
    bundle.context = (EGLContext) 1;
    environment.mainWindowBundle = (basic_render_window_t *) &bundle;
    gl_make_current(&bundle);
    assert(applied == 0);
    gl_swap_interval(0);
    bundle.state = STATE_RENDERER_NEW_WINDOW;
    gl_swap_buffers();
    assert(applied == 0);
    gl_swap_interval(1);
    bundle.state = STATE_RENDERER_NEW_WINDOW;
    gl_swap_buffers();
    assert(applied == 1);
    environment.force_vsync = true;
    gl_swap_interval(0);
    assert(applied == 1 && bundle.swapInterval == 0);
    bundle.state = STATE_RENDERER_NEW_WINDOW;
    gl_swap_buffers();
    assert(applied == 1);
    environment.force_vsync = false;
    gl_make_current(&bundle);
    assert(applied == 0);
    printf("PASS: swap interval restored across surface replacement and context rebinding (%d applications)\n", applications);
    return 0;
}
