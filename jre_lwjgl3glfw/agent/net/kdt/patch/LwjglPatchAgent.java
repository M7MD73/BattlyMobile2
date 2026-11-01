package net.kdt.patch;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.kdt.patch.asm.ClassReader;
import net.kdt.patch.asm.ClassVisitor;
import net.kdt.patch.asm.ClassWriter;
import net.kdt.patch.asm.Label;
import net.kdt.patch.asm.MethodVisitor;
import net.kdt.patch.asm.Opcodes;

public class LwjglPatchAgent {
    private static final String LWJGL_CONTEXT_TRANSFORMER =
            "net/labymod/core/loader/vanilla/launchwrapper/transformer/patch/lwjgl/LWJGLContextTransformer";
    private static final String LABY_DEFAULT_BLUR_RENDERER =
            "net/labymod/core/client/render/draw/DefaultBlurRenderer";
    private static final String LABY_DYNAMIC_BACKGROUND_CONTROLLER =
            "net/labymod/core/client/gui/background/DynamicBackgroundController";
    private static final String LABY_189_MIXIN_MINECRAFT =
            "net/labymod/v1_8_9/mixins/client/MixinMinecraft";
    private static final String MC_189_MINECRAFT =
            "ave";
    private static final String LABY_DEFAULT_OVERLAY_REGISTRY =
            "net/labymod/core/client/gui/screen/activity/DefaultOverlayRegistry";
    private static final String LABY_DEFAULT_NAVIGATION_REGISTRY =
            "net/labymod/core/client/gui/navigation/DefaultNavigationRegistry";
    private static final String MC_EDIT_BOX =
            "net/minecraft/client/gui/components/EditBox";
    private static final String MC_MULTI_LINE_EDIT_BOX =
            "net/minecraft/client/gui/components/MultiLineEditBox";

    public static void premain(String agentArgs, Instrumentation inst) {
        initializeAndroidAudioProvider();
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null) return null;
                try {
                    if (className.startsWith("de/maxhenkel/") && className.endsWith("/LibraryLoader")) {
                        byte[] codec = CodecNativeTransformer.transform(loader, className, classfileBuffer);
                        if (codec != null) return codec;
                    }
                    if ("top/fifthlight/touchcontroller/relocated/top/fifthlight/combine/platform_1_20_x/TextMeasurerImpl".equals(className)) {
                        return patchTouchControllerTextConstraints(classfileBuffer);
                    }
                    if ("imgui/moulberry92/ImGui".equals(className)) {
                        return ImGuiNativeTransformer.transform(loader, classfileBuffer);
                    }
                    if (!className.startsWith("net/kdt/patch/")
                            && containsAscii(classfileBuffer, "javax/sound/sampled/AudioSystem")) {
                        byte[] patchedAudio = patchJavaSoundDiscovery(className, classfileBuffer);
                        if (patchedAudio != null) return patchedAudio;
                    }
                    if ("org/lwjgl/system/MemoryUtil".equals(className) && is32BitProcess()) {
                        return patchArm32UnalignedFloatWrites(classfileBuffer);
                    }
                    if ("org/lwjgl/opengl/GL".equals(className)) {
                        return patchGlClass(classfileBuffer);
                    }
                    if ("org/spongepowered/asm/mixin/MixinEnvironment".equals(className)) {
                        return patchMixinEnvironment(classfileBuffer);
                    }
                    if (MC_EDIT_BOX.equals(className) || MC_MULTI_LINE_EDIT_BOX.equals(className)) {
                        return patchMinecraftTextInput(className, classfileBuffer);
                    }
                    if (LWJGL_CONTEXT_TRANSFORMER.equals(className)) {
                        return patchLabyLwjglContextTransformer(classfileBuffer);
                    }
                    if (LABY_DEFAULT_BLUR_RENDERER.equals(className)) {
                        return patchLabyDefaultBlurRenderer(classfileBuffer);
                    }
                    if (LABY_DYNAMIC_BACKGROUND_CONTROLLER.equals(className)) {
                        return patchLabyDynamicBackgroundController(classfileBuffer);
                    }
                    if (LABY_DEFAULT_OVERLAY_REGISTRY.equals(className)) {
                        return patchLabyDefaultOverlayRegistry(classfileBuffer);
                    }
                    if (LABY_DEFAULT_NAVIGATION_REGISTRY.equals(className)) {
                        return patchLabyDefaultNavigationRegistry(classfileBuffer);
                    }
                    if (LABY_189_MIXIN_MINECRAFT.equals(className)
                            || MC_189_MINECRAFT.equals(className)
                            || containsAscii(classfileBuffer, "labyMod$fireScreenOpenEvent")) {
                        return patchLaby189MixinMinecraft(classfileBuffer);
                    }
                } catch (Throwable t) {
                    System.err.println("[LwjglPatchAgent] Failed to patch " + className + ": " + t);
                    t.printStackTrace();
                }
                return null;
            }
        }, false);
        System.out.println("[LwjglPatchAgent] Registered Android text input + ARM32 memory + GL + MixinEnvironment + LabyMod patchers.");
    }

    static byte[] patchTouchControllerTextConstraints(byte[] original) {
        ClassReader reader = new ClassReader(makeAsm96Readable(original));
        ClassWriter writer = new ClassWriter(reader, 0);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                super.visit(readClassVersion(original), access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String desc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && "coerceIn".equals(method)
                                && "(III)I".equals(desc)
                                && owner.startsWith("top/fifthlight/touchcontroller/relocated/kotlin/ranges/")) {
                            changed[0] = true;
                            owner = "net/kdt/patch/TouchLayoutCompat";
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : null;
    }

    static byte[] patchMinecraftTextInput(String className, byte[] classfileBuffer) {
        final int originalClassVersion = readClassVersion(classfileBuffer);
        ClassReader reader = new ClassReader(makeAsm96Readable(classfileBuffer));
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                // ASM 9.6 predates Java 25. The reader sees a compatible header,
                // while the transformed class keeps Minecraft's original version.
                super.visit(originalClassVersion, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"setFocused".equals(name) || !"(Z)V".equals(descriptor)) {
                    return delegate;
                }
                patched[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "net/kdt/patch/TextInputBridge",
                                    "onTextFocusChanged", "(Z)V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        if (!patched[0]) return null;
        System.out.println("[BattlyInput] Automatic keyboard focus hook installed in " + className);
        return writer.toByteArray();
    }

    private static int readClassVersion(byte[] classfileBuffer) {
        if (classfileBuffer == null || classfileBuffer.length < 8) return 0;
        return ((classfileBuffer[6] & 0xff) << 8) | (classfileBuffer[7] & 0xff);
    }

    private static byte[] makeAsm96Readable(byte[] classfileBuffer) {
        int version = readClassVersion(classfileBuffer);
        if (version <= Opcodes.V22) return classfileBuffer;
        byte[] readable = classfileBuffer.clone();
        readable[6] = (byte) (Opcodes.V22 >>> 8);
        readable[7] = (byte) Opcodes.V22;
        return readable;
    }

    static byte[] patchJavaSoundDiscovery(String className, byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, 0);
        final boolean[] patched = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String calledName,
                                                String calledDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "javax/sound/sampled/AudioSystem".equals(owner)
                                && (("getMixerInfo".equals(calledName)
                                        && "()[Ljavax/sound/sampled/Mixer$Info;".equals(calledDescriptor))
                                    || ("getMixer".equals(calledName)
                                        && "(Ljavax/sound/sampled/Mixer$Info;)Ljavax/sound/sampled/Mixer;"
                                                .equals(calledDescriptor)))) {
                            owner = "net/kdt/patch/audio/AndroidAudioSystemBridge";
                            patched[0] = true;
                        }
                        super.visitMethodInsn(opcode, owner, calledName, calledDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (patched[0]) {
            System.out.println("[BattlyAudio] Patched JavaSound discovery in " + className);
            return writer.toByteArray();
        }
        return null;
    }

    private static void initializeAndroidAudioProvider() {
        try {
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info requestedLine = new DataLine.Info(TargetDataLine.class, format);
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if ("Battly".equals(info.getVendor())
                        && info.getDescription().startsWith("Android microphone capture")) {
                    Mixer mixer = AudioSystem.getMixer(info);
                    System.out.println("[BattlyAudio] JavaSound microphone mixer available before mod loading; "
                            + "pcm16k=" + mixer.isLineSupported(requestedLine));
                    return;
                }
            }
            System.err.println("[BattlyAudio] JavaSound microphone mixer was not registered");
        } catch (Throwable error) {
            System.err.println("[BattlyAudio] Failed to initialize JavaSound microphone provider: " + error);
        }
    }

    static boolean is32BitProcess() {
        String dataModel = System.getProperty("sun.arch.data.model", "").trim();
        if ("32".equals(dataModel)) return true;
        if ("64".equals(dataModel)) return false;

        String architecture = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return "arm".equals(architecture)
                || "aarch32".equals(architecture)
                || "x86".equals(architecture)
                || architecture.matches("i[3-6]86");
    }

    /**
     * LWJGL 3.3.x writes floats with Unsafe.putFloat. ARM32 faults when a vertex
     * format places a float at an unaligned offset, as Minecraft's line format
     * does. Keep the fast aligned write and serialize only unaligned floats.
     */
    static byte[] patchArm32UnalignedFloatWrites(byte[] classfileBuffer) {
        if (!containsAscii(classfileBuffer, "UNSAFE")) return null;

        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("memPutFloat".equals(name) && "(JF)V".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    writeArm32SafeMemPutFloat(mv);
                    patched[0] = true;
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched MemoryUtil.memPutFloat for unaligned ARM32 vertex data");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    private static void writeArm32SafeMemPutFloat(MethodVisitor mv) {
        Label unaligned = new Label();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.LLOAD, 0);
        mv.visitLdcInsn(3L);
        mv.visitInsn(Opcodes.LAND);
        mv.visitInsn(Opcodes.LCONST_0);
        mv.visitInsn(Opcodes.LCMP);
        mv.visitJumpInsn(Opcodes.IFNE, unaligned);

        mv.visitFieldInsn(Opcodes.GETSTATIC, "org/lwjgl/system/MemoryUtil", "UNSAFE", "Lsun/misc/Unsafe;");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitVarInsn(Opcodes.LLOAD, 0);
        mv.visitVarInsn(Opcodes.FLOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "sun/misc/Unsafe", "putFloat", "(Ljava/lang/Object;JF)V", false);
        mv.visitInsn(Opcodes.RETURN);

        mv.visitLabel(unaligned);
        mv.visitVarInsn(Opcodes.FLOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToRawIntBits", "(F)I", false);
        mv.visitVarInsn(Opcodes.ISTORE, 3);
        for (int byteIndex = 0; byteIndex < 4; byteIndex++) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "org/lwjgl/system/MemoryUtil", "UNSAFE", "Lsun/misc/Unsafe;");
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitVarInsn(Opcodes.LLOAD, 0);
            if (byteIndex > 0) {
                mv.visitLdcInsn((long) byteIndex);
                mv.visitInsn(Opcodes.LADD);
            }
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            if (byteIndex > 0) {
                mv.visitIntInsn(Opcodes.BIPUSH, byteIndex * 8);
                mv.visitInsn(Opcodes.IUSHR);
            }
            mv.visitInsn(Opcodes.I2B);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "sun/misc/Unsafe", "putByte", "(Ljava/lang/Object;JB)V", false);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static byte[] patchGlClass(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        final boolean[] hasInitCapabilities = {false};
        final boolean[] hasCreateFromCurrent = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("initCapabilities".equals(name) && "()V".equals(descriptor)) {
                    hasInitCapabilities[0] = true;
                } else if ("createFromCurrent".equals(name) && "()Lorg/lwjgl/opengl/GL;".equals(descriptor)) {
                    hasCreateFromCurrent[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (hasInitCapabilities[0] && hasCreateFromCurrent[0]) return null;

        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitEnd() {
                if (!hasInitCapabilities[0]) {
                    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "initCapabilities", "()V", null, null);
                    mv.visitCode();
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL", "createCapabilities",
                            "()Lorg/lwjgl/opengl/GLCapabilities;", false);
                    mv.visitInsn(Opcodes.POP);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 0);
                    mv.visitEnd();
                    System.out.println("[LwjglPatchAgent] Patched org.lwjgl.opengl.GL: added initCapabilities()");
                }
                if (!hasCreateFromCurrent[0]) {
                    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createFromCurrent",
                            "()Lorg/lwjgl/opengl/GL;", null, null);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(1, 0);
                    mv.visitEnd();
                    System.out.println("[LwjglPatchAgent] Patched org.lwjgl.opengl.GL: added createFromCurrent()");
                }
                super.visitEnd();
            }
        }, ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }

    static byte[] patchMixinEnvironment(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"setCompatibilityLevel".equals(name)) return mv;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ATHROW) {
                            super.visitInsn(Opcodes.POP);
                            super.visitInsn(Opcodes.RETURN);
                            System.out.println("[LwjglPatchAgent] MixinEnvironment.setCompatibilityLevel: suppressed IllegalArgumentException -> return (void)");
                        } else {
                            super.visitInsn(opcode);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    static byte[] patchLabyLwjglContextTransformer(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"transform".equals(name) || !"(Ljava/lang/String;Ljava/lang/String;[B)[B".equals(descriptor)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label continueOriginal = new Label();
                        Label checkName = new Label();
                        Label returnBytes = new Label();

                        super.visitVarInsn(Opcodes.ALOAD, 3);
                        super.visitJumpInsn(Opcodes.IFNONNULL, checkName);
                        super.visitInsn(Opcodes.ACONST_NULL);
                        super.visitInsn(Opcodes.ARETURN);

                        super.visitLabel(checkName);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitJumpInsn(Opcodes.IFNULL, continueOriginal);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitLdcInsn("org.lwjgl.");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
                        super.visitJumpInsn(Opcodes.IFNE, returnBytes);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitLdcInsn("org/lwjgl/");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, continueOriginal);

                        super.visitLabel(returnBytes);
                        super.visitVarInsn(Opcodes.ALOAD, 3);
                        super.visitInsn(Opcodes.ARETURN);

                        super.visitLabel(continueOriginal);
                        patched[0] = true;
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod LWJGLContextTransformer: skipping org.lwjgl.* classes");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    static byte[] patchLabyDefaultBlurRenderer(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<init>".equals(name) && "(Lnet/labymod/api/event/EventBus;)V".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitTypeInsn(Opcodes.NEW, "net/labymod/api/client/gui/screen/widget/attributes/BorderRadius");
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                            "net/labymod/api/client/gui/screen/widget/attributes/BorderRadius",
                            "<init>", "()V", false);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DEFAULT_BLUR_RENDERER, "borderRadius",
                            "Lnet/labymod/api/client/gui/screen/widget/attributes/BorderRadius;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DEFAULT_BLUR_RENDERER, "laby3D",
                            "Lnet/labymod/api/laby3d/Laby3D;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DEFAULT_BLUR_RENDERER, "destinationTarget",
                            "Lnet/labymod/laby3d/api/pipeline/target/RenderTarget;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitTypeInsn(Opcodes.ANEWARRAY, "net/labymod/laby3d/api/pipeline/target/RenderTarget");
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DEFAULT_BLUR_RENDERER, "renderTargets",
                            "[Lnet/labymod/laby3d/api/pipeline/target/RenderTarget;");

                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                if (isVoidMethodToDisable(name, descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod DefaultBlurRenderer: disabled blur render targets on Android");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    private static boolean isVoidMethodToDisable(String name, String descriptor) {
        if ("renderRectangle".equals(name)
                && "(Lnet/labymod/api/client/gui/screen/ScreenContext;Lnet/labymod/api/client/gui/screen/widget/AbstractWidget;I)V".equals(descriptor)) {
            return true;
        }
        if ("onWindowResize".equals(name)
                && "(Lnet/labymod/api/event/client/gui/window/WindowResizeEvent;)V".equals(descriptor)) {
            return true;
        }
        return "resizeRenderTargets".equals(name) && ("()V".equals(descriptor) || "(II)V".equals(descriptor));
    }

    static byte[] patchLabyDynamicBackgroundController(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<init>".equals(name) && "()V".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitIntInsn(Opcodes.SIPUSH, 1024);
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "weatherEffectXOffsets", "[F");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitIntInsn(Opcodes.SIPUSH, 1024);
                    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "weatherEffectZOffsets", "[F");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitTypeInsn(Opcodes.NEW, "java/util/Random");
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Random", "<init>", "()V", false);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "random", "Ljava/util/Random;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "valentine", "Z");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/labymod/api/Laby", "labyAPI", "()Lnet/labymod/api/LabyAPI;", false);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "labyAPI", "Lnet/labymod/api/LabyAPI;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "laby3D", "Lnet/labymod/api/laby3d/Laby3D;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "config",
                            "Lnet/labymod/api/configuration/labymod/main/laby/appearance/DynamicBackgroundConfig;");

                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, LABY_DYNAMIC_BACKGROUND_CONTROLLER, "schematicTarget",
                            "Lnet/labymod/laby3d/api/pipeline/target/RenderTarget;");

                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                if ("isEnabled".equals(name) && "()Z".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitInsn(Opcodes.IRETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                if (isDynamicBackgroundVoidMethodToDisable(name, descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod DynamicBackgroundController: disabled 3D menu background on Android");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    private static boolean isDynamicBackgroundVoidMethodToDisable(String name, String descriptor) {
        if ("render".equals(name)
                && "(Lnet/labymod/api/client/gui/screen/ScreenContext;FFFF)V".equals(descriptor)) {
            return true;
        }
        if ("renderWorld".equals(name)
                && "(Lnet/labymod/api/client/gui/screen/ScreenContext;FFFFF)V".equals(descriptor)) {
            return true;
        }
        return "renderTick".equals(name)
                && "(Lnet/labymod/api/client/render/matrix/Stack;FFFFF)V".equals(descriptor);
    }

    static byte[] patchLaby189MixinMinecraft(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("labyMod$fireScreenOpenEvent".equals(name) && "(Laxu;)Laxu;".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod 1.8.9 screen hook: preserving vanilla screen renderer on Android");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    static byte[] patchLabyDefaultOverlayRegistry(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("onScreenOpen".equals(name)
                        && "(Lnet/labymod/api/event/client/gui/screen/ScreenDisplayEvent;)V".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod DefaultOverlayRegistry: preserving vanilla screens on Android");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    static byte[] patchLabyDefaultNavigationRegistry(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (("onScreenOpenPre".equals(name) || "onScreenOpenPost".equals(name))
                        && "(Lnet/labymod/api/event/client/gui/screen/ScreenDisplayEvent;)V".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }

                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod DefaultNavigationRegistry: preserving vanilla screen navigation on Android");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    private static boolean containsAscii(byte[] bytes, String value) {
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
