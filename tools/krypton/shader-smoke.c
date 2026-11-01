#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>

// Run against the packaged Android library, with a real driver and an offscreen context.
#define LOAD(ret, name, args) ret (*name) args = dlsym(lib, #name); if (!name) return 2

int main(int argc, char **argv) {
    if (argc < 2 || argc > 3) return 2;
    setenv("LIBGL_ES", "3", 1);
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!eglInitialize(display, NULL, NULL)) return 3;
    EGLint attrs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE,
        EGL_OPENGL_ES3_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
    EGLConfig config;
    EGLint count;
    if (!eglChooseConfig(display, attrs, &config, 1, &count) || !count) return 4;
    EGLint surfaceAttrs[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    EGLint contextAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLSurface surface = eglCreatePbufferSurface(display, config, surfaceAttrs);
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttrs);
    if (!eglMakeCurrent(display, surface, surface, context)) return 5;
    void *lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!lib) { puts(dlerror()); return 6; }
    // Krypton's capability probe temporarily binds its own EGL context.
    if (!eglMakeCurrent(display, surface, surface, context)) return 5;
    LOAD(GLuint, glCreateShader, (GLenum));
    LOAD(void, glShaderSource, (GLuint, GLsizei, const GLchar *const *, const GLint *));
    LOAD(void, glCompileShader, (GLuint));
    LOAD(void, glGetShaderiv, (GLuint, GLenum, GLint *));
    LOAD(void, glGetShaderInfoLog, (GLuint, GLsizei, GLsizei *, GLchar *));
    LOAD(GLuint, glCreateProgram, (void));
    LOAD(void, glAttachShader, (GLuint, GLuint));
    LOAD(void, glBindFragDataLocation, (GLuint, GLuint, const GLchar *));
    LOAD(void, glLinkProgram, (GLuint));
    LOAD(void, glGetProgramiv, (GLuint, GLenum, GLint *));
    LOAD(void, glGetProgramInfoLog, (GLuint, GLsizei, GLsizei *, GLchar *));
    LOAD(void, glDeleteProgram, (GLuint));
    LOAD(void, glDeleteShader, (GLuint));
    if (argc == 3) {
        FILE *file = fopen(argv[2], "rb");
        if (!file) return 9;
        fseek(file, 0, SEEK_END);
        long size = ftell(file);
        rewind(file);
        char *source = calloc(1, size + 1);
        if (!source || fread(source, 1, size, file) != (size_t)size) return 9;
        fclose(file);
        GLuint shader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(shader, 1, (const GLchar *const *)&source, NULL);
        glCompileShader(shader);
        GLint ok = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            char log[8192]; glGetShaderInfoLog(shader, sizeof(log), NULL, log);
            fprintf(stderr, "Captured shader: %s\n", log);
        }
        glDeleteShader(shader);
        free(source);
        return ok ? 0 : 7;
    }
    const char *sources[][2] = {
        {"#version 300 es\nprecision highp float; in vec2 pos; void main(){gl_Position=vec4(pos,0.0,1.0);}",
         "#version 300 es\nprecision highp float; out vec4 color; void main(){color=vec4(1.0);}"},
        {"#version 150\nin vec2 pos; void main(){gl_Position=vec4(pos*2-1,0,1);}",
         "#version 150\nout vec4 color; void main(){color=vec4(1);}"},
        {"#version 400\n/* ASCII art \\\n comment */\nin vec2 pos; void main(){gl_Position=vec4(pos,0,1);}",
         "#version 400\nout vec4 color; void main(){color=vec4(1);}"}
    };
    for (int fixture = 0; fixture < 3; ++fixture) {
        for (int i = 0; i < 100; ++i) {
            GLuint shaders[2] = {glCreateShader(GL_VERTEX_SHADER), glCreateShader(GL_FRAGMENT_SHADER)};
            GLuint program = glCreateProgram();
            for (int s = 0; s < 2; ++s) {
                // Exercise source replacement as well as initial ownership.
                glShaderSource(shaders[s], 1, &sources[fixture][s], NULL);
                glShaderSource(shaders[s], 1, &sources[fixture][s], NULL);
                glCompileShader(shaders[s]);
                GLint ok = 0;
                glGetShaderiv(shaders[s], GL_COMPILE_STATUS, &ok);
                if (!ok) {
                    char log[8192]; glGetShaderInfoLog(shaders[s], sizeof(log), NULL, log);
                    fprintf(stderr, "fixture %d shader %d: %s\n", fixture, s, log); return 7;
                }
                glAttachShader(program, shaders[s]);
            }
            glBindFragDataLocation(program, 0, "color");
            glLinkProgram(program);
            GLint ok = 0;
            glGetProgramiv(program, GL_LINK_STATUS, &ok);
            if (!ok) {
                char log[8192]; glGetProgramInfoLog(program, sizeof(log), NULL, log);
                fprintf(stderr, "fixture %d link: %s\n", fixture, log); return 8;
            }
            glDeleteProgram(program);
            glDeleteShader(shaders[0]);
            glDeleteShader(shaders[1]);
        }
        printf("PASS fixture %d: 100 shader compile/bind/link/delete cycles\n", fixture);
    }
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return 0;
}
