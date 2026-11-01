package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BattlyCapeApi {
    private static final String API_BASE = "https://api.battlylauncher.com";
    private static final int MAX_CAPE_BYTES = 100 * 1024;

    private BattlyCapeApi() {
    }

    public static CapeLibrary loadLibrary(Context context) throws IOException {
        AuthContext auth = requireAuth(context);
        HttpURLConnection connection = openConnection(API_BASE + "/api/v2/capes/library", "GET", auth.token);
        JSONObject response = readJson(connection);
        if (!response.optBoolean("success", false)) {
            throw apiError(response, "No se pudo cargar la biblioteca de capas");
        }

        CapeLibrary library = new CapeLibrary();
        library.username = response.optString("username", auth.username);
        library.establishedCapeId = response.optString("established", "");
        library.premium = response.optBoolean("premium", false);
        JSONArray capes = response.optJSONArray("capes");
        if (capes != null) {
            for (int i = 0; i < capes.length(); i++) {
                JSONObject cape = capes.optJSONObject(i);
                if (cape == null) continue;
                String id = cape.optString("id", "");
                String url = cape.optString("url", "");
                if (!Tools.isValidString(id) || !Tools.isValidString(url)) continue;
                library.capes.add(new CapeEntry(
                        id,
                        cape.optString("name", ""),
                        url,
                        cape.optBoolean("selected", id.equals(library.establishedCapeId)),
                        cape.optBoolean("deletable", false),
                        "custom".equals(cape.optString("type", ""))
                ));
            }
        }
        return library;
    }

    public static UploadResult uploadCape(Context context, Uri uri) throws IOException {
        AuthContext auth = requireAuth(context);
        byte[] capeBytes = readCapeBytes(context, uri);
        String boundary = "BattlyCape" + System.currentTimeMillis();
        HttpURLConnection connection = openConnection(API_BASE + "/api/v2/capa/subir", "POST", auth.token);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setChunkedStreamingMode(0);

        try (OutputStream outputStream = connection.getOutputStream()) {
            writeFormField(outputStream, boundary, "token", auth.token);
            outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write("Content-Disposition: form-data; name=\"cape\"; filename=\"cape.png\"\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(capeBytes);
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        JSONObject response = readJson(connection);
        if (response.optInt("status", 0) != 200) {
            throw apiError(response, "No se pudo subir la capa");
        }
        return new UploadResult(response.optString("capeID", ""), response.optString("message", ""));
    }

    public static void setCape(Context context, String capeId) throws IOException {
        AuthContext auth = requireAuth(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", auth.token);
            payload.put("capeID", capeId);
        } catch (Exception exception) {
            throw new IOException(exception);
        }
        JSONObject response = postJson(API_BASE + "/api/v2/capa/establecer", auth.token, payload);
        if (!"OK".equalsIgnoreCase(response.optString("status", ""))) {
            throw apiError(response, "No se pudo establecer la capa");
        }
    }

    public static void clearCape(Context context) throws IOException {
        AuthContext auth = requireAuth(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", auth.token);
        } catch (Exception exception) {
            throw new IOException(exception);
        }
        JSONObject response = postJson(API_BASE + "/api/v2/capa/quitar", auth.token, payload);
        if (!"OK".equalsIgnoreCase(response.optString("status", ""))) {
            throw apiError(response, "No se pudo desactivar la capa");
        }
    }

    public static void deleteCape(Context context, String capeId) throws IOException {
        AuthContext auth = requireAuth(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", auth.token);
            payload.put("capeID", capeId);
        } catch (Exception exception) {
            throw new IOException(exception);
        }
        JSONObject response = postJson(API_BASE + "/api/v2/capa/eliminar", auth.token, payload);
        if (response.optInt("status", 0) != 200) {
            throw apiError(response, "No se pudo eliminar la capa");
        }
    }

    public static Bitmap downloadCapeBitmap(String url) throws IOException {
        byte[] bytes = downloadBytes(withCacheBuster(url));
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) throw new IOException("Capa PNG no válida");
        return bitmap;
    }

    public static String downloadCapeDataUri(String url) throws IOException {
        return "data:image/png;base64," + Base64.encodeToString(
                downloadBytes(withCacheBuster(url)), Base64.NO_WRAP);
    }

    private static String withCacheBuster(String url) {
        return url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
    }

    private static byte[] readCapeBytes(Context context, Uri uri) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) throw new IOException("No se pudo abrir el archivo");
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_CAPE_BYTES) {
                    throw new IOException("La capa supera el límite de 100 KB de Battly");
                }
                outputStream.write(buffer, 0, read);
            }
        }
        byte[] bytes = outputStream.toByteArray();
        if (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IOException("Selecciona una capa PNG válida");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Selecciona una capa PNG válida");
        }
        return bytes;
    }

    private static byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        try (InputStream inputStream = connection.getInputStream()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static JSONObject postJson(String url, String token, JSONObject payload) throws IOException {
        HttpURLConnection connection = openConnection(url, "POST", token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJson(connection);
    }

    private static HttpURLConnection openConnection(String url, String method, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag());
        if (Tools.isValidString(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) connection.setDoOutput(true);
        return connection;
    }

    private static JSONObject readJson(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 400
                ? connection.getInputStream() : connection.getErrorStream();
        String body = stream == null ? "" : Tools.read(stream);
        if (!Tools.isValidString(body)) throw new IOException("Respuesta vacía del servidor");
        try {
            return new JSONObject(body);
        } catch (Exception exception) {
            throw new IOException("Respuesta no válida del servidor: HTTP " + code, exception);
        }
    }

    private static IOException apiError(JSONObject response, String fallback) {
        String message = response.optString("description", response.optString("message", fallback));
        return new IOException(Tools.isValidString(message) ? message : fallback);
    }

    private static void writeFormField(OutputStream outputStream, String boundary, String name, String value) throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static AuthContext requireAuth(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        String token = BattlyPlusManager.getToken(appContext);
        String username = getBattlyUsername(appContext);
        if (!Tools.isValidString(token) || !Tools.isValidString(username)) {
            throw new IOException("Inicia sesión con una cuenta de Battly para gestionar capas");
        }
        return new AuthContext(token, username);
    }

    private static String getBattlyUsername(Context context) {
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
        if (account != null && account.isBattly() && Tools.isValidString(account.username)) {
            return account.username;
        }
        SharedPreferences prefs = context.getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("battly_username", "");
    }

    private static final class AuthContext {
        final String token;
        final String username;

        AuthContext(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }

    public static final class UploadResult {
        public final String capeId;
        public final String message;

        UploadResult(String capeId, String message) {
            this.capeId = capeId;
            this.message = message;
        }
    }

    public static final class CapeLibrary {
        public String username;
        public String establishedCapeId;
        public boolean premium;
        public final List<CapeEntry> capes = new ArrayList<>();
    }

    public static final class CapeEntry {
        public final String id;
        public final String name;
        public final String url;
        public final boolean selected;
        public final boolean deletable;
        public final boolean custom;

        CapeEntry(String id, String name, String url, boolean selected, boolean deletable, boolean custom) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.selected = selected;
            this.deletable = deletable;
            this.custom = custom;
        }
    }
}
