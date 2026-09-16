package net.kdt.pojavlaunch.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NewsLocalePolicy {
    public static final String DEFAULT_LOCALE = "en";
    private static final Set<String> SUPPORTED_LOCALES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("en", "es", "pt", "ru", "zh", "hi", "id", "de", "fr", "ja")));

    private NewsLocalePolicy() {
    }

    public static String resolve(Locale locale) {
        if (locale == null) return DEFAULT_LOCALE;
        String language = locale.getLanguage();
        if (language == null) return DEFAULT_LOCALE;
        language = language.toLowerCase(Locale.ROOT);
        if ("in".equals(language)) language = "id";
        return SUPPORTED_LOCALES.contains(language) ? language : DEFAULT_LOCALE;
    }

    public static List<String> fallbackChain(Locale locale) {
        String resolved = resolve(locale);
        if (DEFAULT_LOCALE.equals(resolved)) return Collections.singletonList(DEFAULT_LOCALE);
        return Arrays.asList(resolved, DEFAULT_LOCALE);
    }

    public static boolean isSupported(String locale) {
        return locale != null && SUPPORTED_LOCALES.contains(locale.toLowerCase(Locale.ROOT));
    }
}
