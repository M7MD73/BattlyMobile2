package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LibraryContentRulesTest {
    @Test
    public void acceptsJarFilesRegardlessOfCase() {
        assertTrue(LibraryContentRules.isSupportedModFile("example.jar"));
        assertTrue(LibraryContentRules.isSupportedModFile("Example.JAR"));
        assertFalse(LibraryContentRules.isSupportedModFile("example.zip"));
        assertFalse(LibraryContentRules.isSupportedModFile("example.jar.zip"));
    }

    @Test
    public void removesProviderPathsAndUnsafeCharacters() {
        assertEquals("friend_mod.jar",
                LibraryContentRules.sanitizeImportedModName("../shared\\friend_mod.jar"));
        assertEquals("mod_name.jar",
                LibraryContentRules.sanitizeImportedModName("mod\u0000name.jar"));
    }

    @Test
    public void rejectsMissingOrInvalidNames() {
        assertEquals("", LibraryContentRules.sanitizeImportedModName(null));
        assertEquals("", LibraryContentRules.sanitizeImportedModName(".."));
        assertEquals("", LibraryContentRules.sanitizeImportedModName("   "));
    }

    @Test
    public void revealsInstalledContentInBatches() {
        assertEquals(5, LibraryContentRules.nextVisibleCount(0, 5));
        assertEquals(15, LibraryContentRules.nextVisibleCount(5, 32));
        assertEquals(25, LibraryContentRules.nextVisibleCount(15, 32));
        assertEquals(32, LibraryContentRules.nextVisibleCount(25, 32));
    }
}
