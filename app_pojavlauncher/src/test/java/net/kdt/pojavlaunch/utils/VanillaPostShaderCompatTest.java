package net.kdt.pojavlaunch.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class VanillaPostShaderCompatTest {
    @Test public void optifineUsesVersionLayoutEvenWhenItsJarLacksPostEffectMarker() {
        assertTrue(VanillaPostShaderCompat.usesModernPostEffects(
                "1.21.4-OptiFine_OptiFine_HD_U_J4_pre2", "1.21.4"));
        assertTrue(VanillaPostShaderCompat.usesModernPostEffects("26.3", null));
        assertFalse(VanillaPostShaderCompat.usesModernPostEffects("1.20.1", null));
        byte[] broken = bytes("{\"battly_android_disabled\":true,\"targets\":[],\"passes\":[]}");
        assertTrue(json(VanillaPostShaderCompat.patchOutline(broken,
                VanillaPostShaderCompat.usesModernPostEffects("1.21.4", null))).has("fragment"));
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static JsonObject json(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test public void modernProgramIsNotReplacedByLegacyPostEffect() {
        byte[] original = bytes("{\"vertex\":\"minecraft:post/sobel\",\"fragment\":\"minecraft:post/entity_sobel\"}");
        assertSame(original, VanillaPostShaderCompat.patchOutline(original, true));
    }

    @Test public void oldPoisonedProgramIsRestoredAndRepairIsIdempotent() {
        byte[] broken = bytes("{\"battly_android_disabled\":true,\"targets\":[],\"passes\":[]}");
        byte[] restored = VanillaPostShaderCompat.patchOutline(broken, true);
        JsonObject program = json(restored);
        assertEquals("minecraft:post/sobel", program.get("vertex").getAsString());
        assertEquals("minecraft:post/entity_sobel", program.get("fragment").getAsString());
        assertEquals("InSampler", program.getAsJsonArray("samplers").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(3, program.getAsJsonArray("uniforms").size());
        assertFalse(program.has("battly_android_disabled"));
        assertFalse(program.has("passes"));
        assertSame(restored, VanillaPostShaderCompat.patchOutline(restored, true));
    }

    @Test public void legacyCrashWorkaroundRemainsAvailable() {
        byte[] patched = VanillaPostShaderCompat.patchOutline(bytes("{\"targets\":[\"swap\"],\"passes\":[]}"), false);
        assertTrue(json(patched).get("battly_android_disabled").getAsBoolean());
        assertEquals(0, json(patched).getAsJsonArray("passes").size());
        assertSame(patched, VanillaPostShaderCompat.patchOutline(patched, false));
    }

    @Test public void customModernProgramIsPreservedByteForByte() {
        byte[] custom = bytes("{\"vertex\":\"custom:vertex\",\"fragment\":\"custom:fragment\",\"uniforms\":[]}");
        assertSame(custom, VanillaPostShaderCompat.patchOutline(custom, true));
    }
}
