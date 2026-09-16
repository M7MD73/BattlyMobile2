package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class NewsLocalePolicyTest {
    @Test
    public void resolvesTheTenPublishedLanguages() {
        assertEquals("es", NewsLocalePolicy.resolve(new Locale("es", "ES")));
        assertEquals("pt", NewsLocalePolicy.resolve(new Locale("pt", "BR")));
        assertEquals("zh", NewsLocalePolicy.resolve(Locale.SIMPLIFIED_CHINESE));
        assertEquals("id", NewsLocalePolicy.resolve(new Locale("in", "ID")));
        assertEquals("ja", NewsLocalePolicy.resolve(Locale.JAPANESE));
    }

    @Test
    public void unsupportedLanguagesFallBackToEnglish() {
        assertEquals("en", NewsLocalePolicy.resolve(new Locale("ca", "ES")));
        assertEquals(Collections.singletonList("en"),
                NewsLocalePolicy.fallbackChain(new Locale("ca", "ES")));
    }

    @Test
    public void supportedLanguagesUseEnglishAsSecondaryFallback() {
        assertEquals(Arrays.asList("fr", "en"),
                NewsLocalePolicy.fallbackChain(Locale.FRENCH));
        assertTrue(NewsLocalePolicy.isSupported("RU"));
        assertFalse(NewsLocalePolicy.isSupported("ko"));
    }
}
