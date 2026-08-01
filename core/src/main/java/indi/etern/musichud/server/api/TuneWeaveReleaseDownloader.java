package indi.etern.musichud.server.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installs TuneWeave from its upstream signed-release manifest. The manifest,
 * selected artifact and checksum are all obtained from TuneWeave itself; no
 * retired NCM distribution metadata is reused.
 */
public final class TuneWeaveReleaseDownloader {
    public static final String MANIFEST_URL = "https://raw.githubusercontent.com/MOPELotus/TuneWeave/main/release-manifest.json";
    private static final Pattern SHA256 = Pattern.compile("(?i)\\b([0-9a-f]{64})\\b");
    private static final HttpClient DOWNLOAD_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private TuneWeaveReleaseDownloader() {
    }

    public static InstalledRelease installOrUpdate(Path root) throws IOException, InterruptedException {
        JsonObject manifest = getJson(MANIFEST_URL);
        String version = string(manifest, "version");
        if (version.isBlank()) {
            throw new IOException("TuneWeave release manifest has no version");
        }
        JsonObject artifact = selectArtifact(manifest.getAsJsonArray("artifacts"));
        String file = string(artifact, "file");
        String downloadUrl = string(artifact, "download_url");
        String checksumUrl = artifact.has("verification") && artifact.get("verification").isJsonObject()
                ? string(artifact.getAsJsonObject("verification"), "checksum_url") : "";
        if (file.isBlank() || downloadUrl.isBlank() || checksumUrl.isBlank()) {
            throw new IOException("TuneWeave release manifest is missing artifact download or checksum metadata");
        }
        Path installDirectory = root.resolve(version);
        Path executable = installDirectory.resolve(file);
        String expected = getChecksum(checksumUrl);
        if (!Files.isRegularFile(executable) || !expected.equalsIgnoreCase(sha256(executable))) {
            Files.createDirectories(installDirectory);
            Path temporary = Files.createTempFile(installDirectory, file + '.', ".download");
            try {
                download(downloadUrl, temporary);
                String actual = sha256(temporary);
                if (!expected.equalsIgnoreCase(actual)) {
                    throw new IOException("TuneWeave checksum verification failed for " + file);
                }
                moveAtomically(temporary, executable);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        executable.toFile().setExecutable(true, true);
        return new InstalledRelease(version, executable);
    }

    private static JsonObject selectArtifact(JsonArray artifacts) throws IOException {
        if (artifacts == null) {
            throw new IOException("TuneWeave release manifest has no artifacts");
        }
        String platform = platform();
        String architecture = architecture();
        for (var element : artifacts) {
            if (!element.isJsonObject()) continue;
            JsonObject artifact = element.getAsJsonObject();
            if (platform.equalsIgnoreCase(string(artifact, "platform"))
                    && architecture.equalsIgnoreCase(string(artifact, "architecture"))) {
                return artifact;
            }
        }
        throw new IOException("TuneWeave has no release for " + platform + '/' + architecture);
    }

    private static JsonObject getJson(String url) throws IOException, InterruptedException {
        String content = getText(url);
        try {
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Invalid TuneWeave release manifest", e);
        }
    }

    private static String getChecksum(String url) throws IOException, InterruptedException {
        Matcher matcher = SHA256.matcher(getText(url));
        if (!matcher.find()) {
            throw new IOException("TuneWeave checksum file does not contain SHA-256");
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private static String getText(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = DOWNLOAD_CLIENT.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30)).header("User-Agent", "MusicHud/TuneWeave-Updater")
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("TuneWeave download request failed (HTTP " + response.statusCode() + ')');
        }
        return response.body();
    }

    private static void download(String url, Path target) throws IOException, InterruptedException {
        HttpResponse<Path> response = DOWNLOAD_CLIENT.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(3)).header("User-Agent", "MusicHud/TuneWeave-Updater")
                        .GET().build(), HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("TuneWeave binary download failed (HTTP " + response.statusCode() + ')');
        }
    }

    private static String sha256(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("Cannot hash TuneWeave artifact", e);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String platform() throws IOException {
        String value = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (value.contains("win")) return "windows";
        if (value.contains("mac") || value.contains("darwin")) return "macos";
        if (value.contains("linux")) return "linux";
        throw new IOException("Unsupported TuneWeave operating system: " + value);
    }

    private static String architecture() throws IOException {
        String value = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (value.equals("amd64") || value.equals("x86_64") || value.equals("x64")) return "x86_64";
        if (value.equals("aarch64") || value.equals("arm64")) return "aarch64";
        throw new IOException("Unsupported TuneWeave architecture: " + value);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    public record InstalledRelease(String version, Path executable) {
    }
}
