package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class BrandNameTranslationsTest {
    private static final Pattern STRING_RESOURCE = Pattern.compile(
            "<string\\s+name=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</string>", Pattern.DOTALL);

    @Test
    public void battlyLauncherIsNeverTranslated() throws Exception {
        File resources = findResourcesDirectory();
        File[] valueDirectories = resources.listFiles(file ->
                file.isDirectory() && file.getName().startsWith("values"));
        assertTrue("No Android values directories found", valueDirectories != null && valueDirectories.length > 0);
        Set<String> brandedKeys = brandedKeys(new File(resources, "values/strings.xml"));
        assertTrue("No Battly Launcher strings found in default resources", !brandedKeys.isEmpty());

        for (File directory : valueDirectories) {
            File strings = new File(directory, "strings.xml");
            if (!strings.isFile()) continue;
            String xml = new String(Files.readAllBytes(strings.toPath()), StandardCharsets.UTF_8);
            Matcher resourcesMatcher = STRING_RESOURCE.matcher(xml);
            while (resourcesMatcher.find()) {
                if (!brandedKeys.contains(resourcesMatcher.group(1))) continue;
                assertTrue(strings.getPath() + ":" + resourcesMatcher.group(1),
                        resourcesMatcher.group(2).contains("Battly Launcher"));
            }
        }
    }

    private static Set<String> brandedKeys(File defaultStrings) throws Exception {
        String xml = new String(Files.readAllBytes(defaultStrings.toPath()), StandardCharsets.UTF_8);
        Set<String> keys = new HashSet<>();
        Matcher matcher = STRING_RESOURCE.matcher(xml);
        while (matcher.find()) {
            if (matcher.group(2).contains("Battly Launcher")) keys.add(matcher.group(1));
        }
        return keys;
    }

    private static File findResourcesDirectory() {
        File moduleResources = new File("src/main/res");
        if (moduleResources.isDirectory()) return moduleResources;
        return new File("app_pojavlauncher/src/main/res");
    }
}
