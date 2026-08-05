package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.tuneweave.TuneWeaveApiClient;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@RegisterMark
public class ApiServerManager implements ServerRegister {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final Path LOG_DIR = Paths.get("music-hud", "logs");
    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
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
    private volatile Process process;
    @Getter
    private BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
    private int triedCount = 0;
    private boolean initialized = false;
    private Thread hook;
    private CompletableFuture<Integer> processFuture;
    private boolean continueRestart;

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
        if (serverConfig.getStartupBinaryApiServerWhenLaunch()) {
            launchApiServerInternal();
        }
    }

    public void stopApiServer() {
        Process running = process;
        continueRestart = false;
        if (running != null) {
            process = null;
            running.destroy();
        }
        removeShutdownHook();
    }

    public void restartApiServer() {
        triedCount = 0;
        stopApiServer();
        CompletableFuture<Integer> previous = processFuture;
        if (previous != null && !previous.isDone()) {
            previous.whenComplete((exitCode, error) -> launchApiServerInternal());
        } else {
            launchApiServerInternal();
        }
    }

    private synchronized void addShutdownHook() {
        if (hook == null) {
            hook = new Thread(this::stopApiServer);
            ICommonEventService.getInstance().registerCommonLifecycleStopping(this::stopApiServer);
            Runtime.getRuntime().addShutdownHook(hook);
        }
    }

    private synchronized void removeShutdownHook() {
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // The shutdown hook calls stopApiServer() while JVM shutdown is already in progress.
            }
            hook = null;
        }
    }

    private void launchApiServerInternal() {
        MusicHud.EXECUTOR.execute(() -> {
            Thread.currentThread().setName("MHWorker-API-Launcher");
            boolean apiAvailable = TuneWeaveApiClient.isAvailable();
            if (!apiAvailable) {
                triedCount = 0;
                startEmbeddedApiServer();
                Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
                if (side == Environment.Side.CLIENT) {
                    addShutdownHook();
                } else if (side == Environment.Side.SERVER) {
                    addShutdownHook();
                }
            } else {
                setApiStatus(BinaryApiServerStatus.RUNNING);
                apiLogger.info("TuneWeave API server is already available");
            }
        });
    }

    private synchronized void startEmbeddedApiServer() {
        if (process != null) {
            return;
        }
        int maxTries = 5;
        if (triedCount >= maxTries) {
            apiLogger.error("Embedded TuneWeave has been stopped due to maximum tries reached.");
            return;
        }
        String binaryExecutableApiServerPathString = serverConfig.getServerApiBinaryExecutablePath();
        Path binaryExecutableApiServerPath = Paths.get(binaryExecutableApiServerPathString);
        Path windowsExePath = Paths.get(binaryExecutableApiServerPathString + ".exe");
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        boolean executable = launchable(binaryExecutableApiServerPath, windows)
                || launchable(windowsExePath, windows);
        boolean exists = Files.isRegularFile(binaryExecutableApiServerPath) || Files.isRegularFile(windowsExePath);
        if (exists) {
            if (executable) {
                triedCount++;
                try {
                    continueRestart = true;
                    setApiStatus(BinaryApiServerStatus.LAUNCHING);

                    Path executablePath;
                    if (launchable(windowsExePath, windows)) {
                        executablePath = windowsExePath;
                    } else {
                        executablePath = binaryExecutableApiServerPath;
                    }

                    ProcessBuilder processBuilder = new ProcessBuilder(executablePath.toString());
                    Map<String, String> env = processBuilder.environment();
                    env.put("TUNEWEAVE_BIND", "127.0.0.1:" + serverConfig.getPort());
                    env.put("TUNEWEAVE_DATA_DIR", Paths.get("music-hud", "tuneweave-data").toAbsolutePath().toString());
                    Process launchedProcess = processBuilder.start();
                    process = launchedProcess;

                    Path logFile;
                    PrintWriter logWriter = null;
                    try {
                        Files.createDirectories(LOG_DIR);
                        logFile = LOG_DIR.resolve("api-server-" + LocalDateTime.now().format(LOG_TIMESTAMP) + ".log");
                        logWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
                    } catch (IOException e) {
                        apiLogger.error("Failed to create log file", e);
                    }

                    final PrintWriter writer = logWriter;

                    CompletableFuture<Integer> future = new CompletableFuture<>();
                    processFuture = future;
                    MusicHud.EXECUTOR.execute(() -> waitForTuneWeave(launchedProcess));

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Console");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(launchedProcess.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (writer != null) writer.println(line);
                                log(line, false);
                            }
                        } catch (IOException e) {
                            apiLogger.error("Error reading stdout", e);
                        }
                    });

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Console");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(launchedProcess.getErrorStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (writer != null) writer.println(line);
                                log(line, true);
                            }
                        } catch (IOException e) {
                            apiLogger.error("Error reading stderr", e);
                        }
                    });

                    MusicHud.EXECUTOR.execute(() -> {
                        Thread.currentThread().setName("MHWorker-API-Daemon");
                        try {
                            int exitCode = launchedProcess.waitFor();
                            if (writer != null) writer.close();
                            if (process == launchedProcess) process = null;
                            if (processFuture == future) processFuture = null;
                            setApiStatus(BinaryApiServerStatus.STOPPED);
                            if (continueRestart) {
                                apiLogger.warn("Api server unexpectedly stopped with code:{}, restarting...", exitCode);
                                future.complete(exitCode);
                                startEmbeddedApiServer();
                            } else {
                                apiLogger.info("Api server stopped with code:{}", exitCode);
                                future.complete(exitCode);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            apiLogger.error("Process wait interrupted", e);
                            future.completeExceptionally(e);
                        }
                    });
                } catch (Exception e) {
                    MusicHud.LOGGER.error("Failed to start TuneWeave at path: \"{}\"", binaryExecutableApiServerPathString, e);
                    setApiStatus(BinaryApiServerStatus.STOPPED);
                    if (processFuture != null) {
                        processFuture.completeExceptionally(e);
                    }
                }
            } else {
                apiLogger.error("TuneWeave binary is not executable: {}", binaryExecutableApiServerPathString);
                setApiStatus(BinaryApiServerStatus.STOPPED);
            }
        } else {
            apiLogger.error("TuneWeave binary was not found: {}", binaryExecutableApiServerPathString);
            setApiStatus(BinaryApiServerStatus.STOPPED);
        }
    }

    private static boolean launchable(Path path, boolean windows) {
        return Files.isRegularFile(path) && (windows || Files.isExecutable(path));
    }

    private void waitForTuneWeave(Process launchedProcess) {
        for (int attempt = 0; attempt < 120; attempt++) {
            if (process != launchedProcess || !launchedProcess.isAlive()) {
                return;
            }
            if (TuneWeaveApiClient.isAvailable()) {
                setApiStatus(BinaryApiServerStatus.RUNNING);
                apiLogger.info("TuneWeave API server started");
                return;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (process == launchedProcess && launchedProcess.isAlive()) {
            apiLogger.error("TuneWeave did not become healthy within 30 seconds");
            launchedProcess.destroy();
        }
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
