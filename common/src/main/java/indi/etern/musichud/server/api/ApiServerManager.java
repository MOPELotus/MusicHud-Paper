package indi.etern.musichud.server.api;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.*;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.http.ApiClient;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RegisterMark
public class ApiServerManager implements ServerRegister {
    private static ApiServerManager register;
    private static final Logger apiLogger = LogManager.getLogger(MusicHud.LOGGER_BASE_NAME + "/API");
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final Object PROCESS_LOCK = new Object();
    private static Process process;
    private static boolean desiredRunning = false;
    @Getter
    private static BinaryApiServerStatus binaryApiServerStatus = BinaryApiServerStatus.STOPPED;
    @Getter
    private static final List<Consumer<BinaryApiServerStatus>> apiStatusListeners = new ArrayList<>();
    private static final int maxTries = 5;
    private static int triedCount = 0;

    public enum BinaryApiServerStatus {
        STOPPED, LAUNCHING, RUNNING;

        public String i18nKey() {
            return MusicHud.MOD_ID + ".text.binaryApiServerStatus." + name();
        }
    }

    public void log(String s, boolean error) {
        if (error || s.contains("[ERROR]")) {
            apiLogger.error(s.replace("[ERROR]", ""));
        } else {
            apiLogger.debug(s.replace("[INFO]", ""));
        }
    }

    @Override
    public void register() {
        register = this;
        MusicHud.EXECUTOR.execute(() -> {
            Thread.currentThread().setName("API Server Launcher");
            boolean apiAvailable = ApiClient.checkAvailable();
            if (serverConfig.getStartupBinaryApiServerWhenLaunch() && !apiAvailable) {
                synchronized (PROCESS_LOCK) {
                    triedCount = 0;
                    desiredRunning = true;
                }
                startEmbeddedApiServer();
                Environment.Side side = MusicHud.getCurrentEnvironment().getSide();
                if (side == Environment.Side.CLIENT) {
                    IClientEventService.getInstance().registerClientLifecycleStopping(ApiServerManager::stopApiServer);
                } else if (side == Environment.Side.SERVER) {
                    IServerEventService.getInstance().registerServerLifecycleStopping(ApiServerManager::stopApiServer);
                }
            } else if (apiAvailable) {
                apiLogger.info("API Server has been launched externally");
            }
        });
    }

    public static void stopApiServer() {
        Process currentProcess;
        synchronized (PROCESS_LOCK) {
            desiredRunning = false;
            currentProcess = process;
            if (currentProcess == null || !currentProcess.isAlive()) {
                process = null;
            }
        }
        if (currentProcess != null) {
            currentProcess.destroy();
        } else {
            setApiStatus(BinaryApiServerStatus.STOPPED);
        }
    }

    public static void restartApiServer() {
        if (register == null) {
            return;
        }
        Process currentProcess;
        synchronized (PROCESS_LOCK) {
            desiredRunning = true;
            triedCount = 0;
            currentProcess = process;
        }
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
        } else {
            register.startEmbeddedApiServer();
        }
    }

    private void startEmbeddedApiServer() {
        Path launchPath;
        synchronized (PROCESS_LOCK) {
            desiredRunning = true;
            if (process != null && process.isAlive()) {
                return;
            }
            if (triedCount >= maxTries) {
                desiredRunning = false;
                setApiStatus(BinaryApiServerStatus.STOPPED);
                apiLogger.error("Embedded API Server has been stopped due to maximum tries reached.");
                return;
            }

            String configuredPathString = serverConfig.getServerApiBinaryExecutablePath();
            Path configuredPath = Paths.get(configuredPathString);
            Path windowsExePath = Paths.get(configuredPathString + ".exe");

            if (Files.exists(configuredPath) && Files.isExecutable(configuredPath)) {
                launchPath = configuredPath;
            } else if (Files.exists(windowsExePath) && Files.isExecutable(windowsExePath)) {
                launchPath = windowsExePath;
            } else {
                if (Files.exists(configuredPath) || Files.exists(windowsExePath)) {
                    apiLogger.error("Embedded API executable exists but is not executable: {}", configuredPathString);
                } else {
                    apiLogger.error("Embedded API executable not found: {}", configuredPathString);
                }
                desiredRunning = false;
                setApiStatus(BinaryApiServerStatus.STOPPED);
                return;
            }

            triedCount++;
            setApiStatus(BinaryApiServerStatus.LAUNCHING);
        }

        try {
            Process launchedProcess = Runtime.getRuntime().exec(new String[]{launchPath.toString()});
            synchronized (PROCESS_LOCK) {
                process = launchedProcess;
            }

            MusicHud.EXECUTOR.execute(() -> {
                Thread.currentThread().setName("API Console");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(launchedProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Server started successfully") && binaryApiServerStatus == BinaryApiServerStatus.LAUNCHING) {
                            setApiStatus(BinaryApiServerStatus.RUNNING);
                            apiLogger.info("Api server started");
                        }
                        log(line, false);
                    }
                } catch (IOException e) {
                    apiLogger.error("Error reading stdout", e);
                }
            });

            MusicHud.EXECUTOR.execute(() -> {
                Thread.currentThread().setName("API Console");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(launchedProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log(line, true);
                    }
                } catch (IOException e) {
                    apiLogger.error("Error reading stderr", e);
                }
            });

            MusicHud.EXECUTOR.execute(() -> {
                Thread.currentThread().setName("API Daemon");
                try {
                    int exitCode = launchedProcess.waitFor();
                    boolean latestProcess;
                    boolean shouldRestart;
                    synchronized (PROCESS_LOCK) {
                        latestProcess = process == launchedProcess;
                        if (latestProcess) {
                            process = null;
                        }
                        shouldRestart = desiredRunning && latestProcess;
                    }
                    if (latestProcess) {
                        setApiStatus(BinaryApiServerStatus.STOPPED);
                    }
                    if (shouldRestart) {
                        apiLogger.warn("Api server unexpectedly stopped with code:{}, restarting...", exitCode);
                        startEmbeddedApiServer();
                    } else if (latestProcess) {
                        apiLogger.info("Api server stopped with code:{}", exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    apiLogger.error("Process wait interrupted", e);
                }
            });
        } catch (Exception e) {
            synchronized (PROCESS_LOCK) {
                process = null;
                desiredRunning = false;
            }
            setApiStatus(BinaryApiServerStatus.STOPPED);
            MusicHud.LOGGER.error("Failed to call binary server at path: \"{}\"", launchPath, e);
        }
    }

    private static void setApiStatus(BinaryApiServerStatus status) {
        binaryApiServerStatus = status;
        apiStatusListeners.forEach(l -> l.accept(status));
    }
}
