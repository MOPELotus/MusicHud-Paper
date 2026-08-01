package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.impl.tuneweave.TuneWeaveApiClient;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RegisterMark
public class ApiServerManager implements ServerRegister {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final Path LOG_DIR = Paths.get("music-hud", "logs");
    private static final Path TUNEWEAVE_DIR = Paths.get("music-hud", "tuneweave");
    private static ClientConfig clientConfig;
    @Getter
    private static ApiServerManager instance;

    static {
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
            try {
                clientConfig = ClientConfig.getInstance();
            } catch (UnsupportedOperationException e) {
                clientConfig = null;
            }
        }
    }

    private final Logger apiLogger = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/API");
    @Getter
    private final List<Consumer<BinaryApiServerStatus>> apiStatusListeners = new ArrayList<>();
    @Getter
    private BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
    private boolean initialized = false;
    private volatile Process managedProcess;

    public void clearLogs() {
        try (var stream = Files.list(getLogDir())) {
            stream.filter(p -> p.getFileName().toString().endsWith(".log"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public void log(String s, boolean error) {
        if (error || s.contains("ERROR")) {
            apiLogger.error(s.replace("[ERROR]", ""));
        } else {
            apiLogger.debug(s.replace("[INFO]", ""));
        }
    }

    @Override
    public void register() {
        instance = this;
        if (initialized) {
            return;
        }
        initialized = true;
        if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT && clientConfig != null
                && !clientConfig.getEnabledInIntegratedServer()) {
            return;
        }
        launchApiServerInternal();
    }

    public void stopApiServer() {
        stopManagedProcess();
        setApiStatus(BinaryApiServerStatus.STOPPED);
    }

    public void restartApiServer() {
        launchApiServerInternal();
    }

    /** Explicit client-side action from the download dialog. */
    public void downloadAndStartTuneWeave() {
        MusicHud.EXECUTOR.execute(this::launchManagedTuneWeave);
    }

    private void launchApiServerInternal() {
        MusicHud.EXECUTOR.execute(() -> {
            Thread.currentThread().setName("MHWorker-API-Launcher");
            if (isTuneWeaveAvailable()) {
                apiLogger.info("TuneWeave is available at {}", tuneWeaveBaseUrl());
                setApiStatus(BinaryApiServerStatus.RUNNING);
            } else if (shouldManageTuneWeave()) {
                launchManagedTuneWeave();
            } else {
                apiLogger.error("TuneWeave is unavailable at {}. Enable local/internal management to download and start it automatically.", tuneWeaveBaseUrl());
                setApiStatus(BinaryApiServerStatus.STOPPED);
            }
        });
    }

    private boolean shouldManageTuneWeave() {
        return serverConfig.getManageTuneWeaveInternally();
    }

    private void launchManagedTuneWeave() {
        setApiStatus(BinaryApiServerStatus.LAUNCHING);
        try {
            TuneWeaveReleaseDownloader.InstalledRelease release = TuneWeaveReleaseDownloader.installOrUpdate(TUNEWEAVE_DIR);
            Path logFile = getLogDir().resolve("tuneweave.log");
            Files.createDirectories(logFile.getParent());
            managedProcess = new ProcessBuilder(release.executable().toAbsolutePath().toString())
                    .directory(release.executable().getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopManagedProcess, "MusicHud-TuneWeave-Shutdown"));
            apiLogger.info("Started managed TuneWeave {} from {}", release.version(), release.executable());
            for (int attempt = 0; attempt < 20; attempt++) {
                if (isTuneWeaveAvailable()) {
                    setApiStatus(BinaryApiServerStatus.RUNNING);
                    return;
                }
                Thread.sleep(500L);
            }
            apiLogger.error("Managed TuneWeave started but did not become healthy at {}. See {}", tuneWeaveBaseUrl(), logFile);
            stopManagedProcess();
            setApiStatus(BinaryApiServerStatus.STOPPED);
        } catch (Exception e) {
            apiLogger.error("Unable to download, verify or start managed TuneWeave", e);
            setApiStatus(BinaryApiServerStatus.STOPPED);
        }
    }

    private void stopManagedProcess() {
        Process process = managedProcess;
        managedProcess = null;
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private boolean isTuneWeaveAvailable() {
        return TuneWeaveApiClient.isAvailable();
    }

    private String tuneWeaveBaseUrl() {
        return TuneWeaveApiClient.baseUrl();
    }

    public Path getLogDir() {
        return LOG_DIR;
    }

    public long[] getLogStats() {
        try {
            Files.createDirectories(LOG_DIR);
        } catch (IOException ignored) {
        }
        try (var stream = Files.list(LOG_DIR)) {
            long[] result = {0, 0};
            stream.filter(p -> p.getFileName().toString().endsWith(".log")).forEach(p -> {
                result[0]++;
                try {
                    result[1] += Files.size(p);
                } catch (IOException ignored) {
                }
            });
            return result;
        } catch (IOException e) {
            return new long[]{0, 0};
        }
    }

    private void setApiStatus(BinaryApiServerStatus status) {
        binaryApiServerStatus = status;
        apiStatusListeners.forEach(l -> l.accept(status));
    }

    public enum BinaryApiServerStatus {
        STOPPED, LAUNCHING, RUNNING;

        public String i18nKey() {
            return MusicHud.MOD_ID + ".text.binaryApiServerStatus." + name();
        }
    }
}
