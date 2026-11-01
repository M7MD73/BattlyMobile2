package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.system.Os;
import androidx.test.platform.app.InstrumentationRegistry;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.R;
import org.junit.Test;
import java.io.*;
import java.util.UUID;
import static org.junit.Assert.*;

public class SecurityRegressionTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private String consume(Context context) {
        String token = BattlyOAuthSession.claimToken(context, "discord", "test-screen");
        if (token != null) BattlyOAuthSession.complete(context, "test-screen");
        return token;
    }

    @Test public void oauthIsSingleUseAcrossBothCallbackChannelsAndRecreation() {
        Context isolated = new ContextWrapper(context()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("security_test_" + name, mode);
            }
        };
        isolated.getSharedPreferences("battly_oauth", 0).edit().clear().commit();
        assertFalse(BattlyOAuthSession.accept(isolated, "discord", "dummy", "nonce"));
        BattlyOAuthSession.begin(isolated, "discord", "nonce");
        assertFalse(BattlyOAuthSession.accept(isolated, "discord", "dummy", "wrong"));
        assertTrue(BattlyOAuthSession.accept(isolated, "discord", "dummy", "nonce"));
        assertFalse(BattlyOAuthSession.accept(isolated, "discord", "dummy", "nonce"));
        assertEquals("dummy", consume(isolated));
        BattlyOAuthSession.begin(isolated, "discord", "recreation");
        assertTrue(BattlyOAuthSession.accept(isolated, "discord", "recoverable", "recreation"));
        assertEquals("recoverable", BattlyOAuthSession.claimToken(isolated, "discord", "old-screen"));
        assertNull(BattlyOAuthSession.claimToken(isolated, "discord", "new-screen"));
        BattlyOAuthSession.release("old-screen");
        assertEquals("recoverable", BattlyOAuthSession.claimToken(isolated, "discord", "new-screen"));
        BattlyOAuthSession.complete(isolated, "old-screen");
        assertTrue(BattlyOAuthSession.owns("new-screen"));
        BattlyOAuthSession.complete(isolated, "new-screen");
        assertNull(BattlyOAuthSession.claimToken(isolated, "discord", "third-screen"));
        assertNull(consume(isolated));
        BattlyOAuthSession.begin(isolated, "discord", "new");
        assertFalse(BattlyOAuthSession.accept(isolated, "discord", "dummy", "nonce"));
        assertTrue(BattlyOAuthSession.accept(isolated, "discord", "dummy", "new"));
        assertEquals("dummy", consume(isolated));
    }

    @Test public void realDocumentsProviderKeepsNormalOperationsAndRejectsTreeEscape() throws Exception {
        Tools.initStorageConstants(context());
        File root = new File(Tools.DIR_GAME_HOME, "security-test-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        File shared = new File(root, "shared");
        File victim = new File(root, "victim.txt");
        assertTrue(shared.mkdir());
        try (FileOutputStream out = new FileOutputStream(victim)) { out.write(42); }
        String authority = context().getString(R.string.storageProviderAuthorities);
        Uri tree = DocumentsContract.buildTreeDocumentUri(authority, shared.getAbsolutePath());
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, shared.getAbsolutePath());
        try {
            Uri file = DocumentsContract.createDocument(context().getContentResolver(), parent, "text/plain", "normal.txt");
            assertNotNull(file);
            try (OutputStream out = context().getContentResolver().openOutputStream(file)) { out.write(17); }
            try (InputStream in = context().getContentResolver().openInputStream(file)) { assertEquals(17, in.read()); }
            Uri renamed = DocumentsContract.renameDocument(context().getContentResolver(), file, "renamed.txt");
            assertNotNull(renamed);
            assertTrue(new File(shared, "renamed.txt").isFile());
            Uri escape = DocumentsContract.buildDocumentUriUsingTree(tree, shared.getAbsolutePath() + "/../victim.txt");
            try (InputStream ignored = context().getContentResolver().openInputStream(escape)) { fail("Tree escape accepted"); }
            catch (SecurityException | FileNotFoundException expected) { }
            try {
                Uri bad = DocumentsContract.createDocument(context().getContentResolver(), parent, "text/plain", "../escape.txt");
                assertNull(bad);
            } catch (SecurityException | FileNotFoundException expected) { }
            assertFalse(new File(root, "escape.txt").exists());
            assertTrue(DocumentsContract.deleteDocument(context().getContentResolver(), renamed));
            try (InputStream in = new FileInputStream(victim)) { assertEquals(42, in.read()); }
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(root);
        }
    }

    @Test public void androidCanonicalResolutionRejectsExternalSymlink() throws Exception {
        File root = new File(context().getCacheDir(), "safe-path-" + UUID.randomUUID());
        File outside = new File(context().getCacheDir(), "outside-path-" + UUID.randomUUID());
        assertTrue(root.mkdir()); assertTrue(outside.mkdir());
        File link = new File(root, "link");
        try {
            Os.symlink(outside.getAbsolutePath(), link.getAbsolutePath());
            try { SafeFilePaths.resolveRelative(root, "link/file"); fail(); }
            catch (IOException expected) { }
        } finally { link.delete(); root.delete(); outside.delete(); }
    }

    @Test public void externalPluginRequiresTrustAndLoadsAfterApproval() throws Exception {
        NativeLibraryPluginManager.Plugin fixture = null;
        for (NativeLibraryPluginManager.Plugin plugin : NativeLibraryPluginManager.discover(context())) {
            if (plugin.packageName.equals("com.tecnobros.battly.securityplugin")) fixture = plugin;
        }
        assertNotNull("Install the local native-plugin-fixture APK first", fixture);
        File emptyInstance = new File(context().getCacheDir(), "plugin-test-" + UUID.randomUUID());
        assertTrue(emptyInstance.mkdir());
        try {
            assertTrue(NativeLibraryPluginManager.setEnabled(context(), fixture.packageName, fixture.signingIdentity, false));
            assertFalse(NativeLibraryPluginManager.isEnabled(context(), fixture.packageName));
            NativeLibraryPluginManager.prepareForLaunch(context(), "1.20.1", emptyInstance);
            assertFalse(NativeLibraryPluginManager.activeNativePaths().contains(fixture.nativeDirectory.getAbsolutePath()));
            assertFalse(NativeLibraryPluginManager.setEnabled(context(), fixture.packageName, "wrong-signer", true));
            assertTrue(NativeLibraryPluginManager.setEnabled(context(), fixture.packageName, fixture.signingIdentity, true));
            assertTrue(NativeLibraryPluginManager.isEnabled(context(), fixture.packageName));
            NativeLibraryPluginManager.prepareForLaunch(context(), "1.20.1", emptyInstance);
            assertTrue(NativeLibraryPluginManager.activeNativePaths().contains(fixture.nativeDirectory.getAbsolutePath()));
            assertTrue(NativeLibraryPluginManager.activeJvmArguments().contains("-Dbattly.security.fixture=" + fixture.nativeDirectory.getCanonicalPath()));
            System.load(new File(fixture.nativeDirectory, "libbattly_security_probe.so").getAbsolutePath());
        } finally {
            NativeLibraryPluginManager.setEnabled(context(), fixture.packageName, fixture.signingIdentity, false);
            NativeLibraryPluginManager.prepareForLaunch(context(), "1.20.1", emptyInstance);
            assertFalse(NativeLibraryPluginManager.activeNativePaths().contains(fixture.nativeDirectory.getAbsolutePath()));
            emptyInstance.delete();
        }
    }

    @Test public void managingInternalSymlinkDoesNotDeleteItsTarget() throws Exception {
        File root = new File(context().getCacheDir(), "provider-test-" + UUID.randomUUID());
        File target = new File(root, "world");
        assertTrue(target.mkdirs());
        File data = new File(target, "level.dat");
        assertTrue(data.createNewFile());
        File alias = new File(root, "alias");
        Os.symlink(target.getAbsolutePath(), alias.getAbsolutePath());
        net.kdt.pojavlaunch.scoped.FolderProvider provider = new net.kdt.pojavlaunch.scoped.FolderProvider();
        for (String name : new String[]{"BASE_DIR", "mContentResolver", "mStorageProviderAuthortiy"}) {
            java.lang.reflect.Field field = provider.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(provider, name.equals("BASE_DIR") ? root : name.equals("mContentResolver")
                    ? context().getContentResolver() : context().getString(R.string.storageProviderAuthorities));
        }
        try {
            String created = provider.createDocument(alias.getAbsolutePath(), "text/plain", "new.txt");
            assertEquals(new File(alias, "new.txt").getAbsolutePath(), created);
            assertTrue(provider.isChildDocument(alias.getAbsolutePath(), created));
            assertFalse(provider.isChildDocument(target.getAbsolutePath(), created));
            assertEquals(created, provider.findDocumentPath(alias.getAbsolutePath(), created).getPath().get(1));
            provider.removeDocument(created, alias.getAbsolutePath());
            assertFalse(new File(target, "new.txt").exists());
            String renamed = provider.renameDocument(alias.getAbsolutePath(), "renamed-alias");
            assertTrue(data.isFile());
            provider.deleteDocument(renamed);
            assertTrue(data.isFile());
            assertFalse(new File(root, "renamed-alias").exists());
        } finally { org.apache.commons.io.FileUtils.deleteDirectory(root); }
    }

    private File modpack(File directory, String path) throws Exception {
        File archive = new File(directory, "fixture.mrpack");
        File payload = new File(directory, "payload.txt");
        try (FileOutputStream out = new FileOutputStream(payload)) { out.write(19); }
        org.json.JSONObject item = new org.json.JSONObject().put("path", path == null ? "config/downloaded.txt" : path)
                .put("downloads", new org.json.JSONArray().put(payload.toURI().toString())).put("fileSize", 1);
        String manifest = new org.json.JSONObject().put("formatVersion", 1).put("game", "minecraft")
                .put("versionId", "test").put("name", "Security fixture")
                .put("files", new org.json.JSONArray().put(item))
                .put("dependencies", new org.json.JSONObject().put("minecraft", "1.20.1").put("fabric-loader", "0.16.14")).toString();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("modrinth.index.json"));
            zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("overrides/config/example.txt"));
            zip.write(17); zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("client-overrides/options.txt"));
            zip.write(18); zip.closeEntry();
        }
        return archive;
    }

    @Test public void installsValidModpackAndRejectsManifestEscapeBeforeDownload() throws Exception {
        File scratch = new File(context().getCacheDir(), "pack-test-" + UUID.randomUUID());
        File instance = new File(scratch, "instance");
        assertTrue(instance.mkdirs());
        try {
            net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi api = new net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi();
            assertNotNull(api.installPackArchive(modpack(scratch, null), instance));
            try (InputStream in = new FileInputStream(new File(instance, "config/downloaded.txt"))) { assertEquals(19, in.read()); }
            try (InputStream in = new FileInputStream(new File(instance, "config/example.txt"))) { assertEquals(17, in.read()); }
            try (InputStream in = new FileInputStream(new File(instance, "options.txt"))) { assertEquals(18, in.read()); }
            try { api.installPackArchive(modpack(scratch, "../victim.txt"), instance); fail("Manifest escape accepted"); }
            catch (IOException expected) { }
            assertFalse(new File(scratch, "victim.txt").exists());
        } finally { org.apache.commons.io.FileUtils.deleteDirectory(scratch); }
    }

    @Test public void curseforgeArchiveWithDefaultOverridesStillInstalls() throws Exception {
        File scratch = new File(context().getCacheDir(), "curseforge-test-" + UUID.randomUUID());
        File instance = new File(scratch, "instance");
        assertTrue(instance.mkdirs());
        File archive = new File(scratch, "fixture.zip");
        String manifest = new org.json.JSONObject().put("manifestType", "minecraftModpack").put("manifestVersion", 1)
                .put("files", new org.json.JSONArray()).put("minecraft", new org.json.JSONObject().put("version", "1.20.1")
                .put("modLoaders", new org.json.JSONArray().put(new org.json.JSONObject().put("id", "forge-47.4.23").put("primary", true)))).toString();
        try {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
                zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("overrides/config/curseforge.txt"));
                zip.write(23); zip.closeEntry();
            }
            net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi api = new net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi("local-test-only");
            assertNotNull(api.installPackArchive(archive, instance));
            try (InputStream in = new FileInputStream(new File(instance, "config/curseforge.txt"))) { assertEquals(23, in.read()); }
            File payload = new File(scratch, "example.txt");
            try (FileOutputStream out = new FileOutputStream(payload)) { out.write(29); }
            String url = payload.toURI().toString();
            net.kdt.pojavlaunch.modloaders.modpacks.api.ModDownloader downloader = new net.kdt.pojavlaunch.modloaders.modpacks.api.ModDownloader(new File(instance, "mods"), true);
            downloader.submitDownload(() -> new net.kdt.pojavlaunch.modloaders.modpacks.api.ModDownloader.FileInfo(url, SafeFilePaths.downloadFileName(url), null));
            downloader.awaitFinish((current, maximum) -> {});
            try (InputStream in = new FileInputStream(new File(instance, "mods/example.txt"))) { assertEquals(29, in.read()); }
        } finally { org.apache.commons.io.FileUtils.deleteDirectory(scratch); }
    }
}
