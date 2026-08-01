package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;

import java.util.function.Supplier;

public interface ServerConfig {
    static ServerConfig getInstance() {
        Environment.Platform platform = MusicHud.getCurrentEnvironment().getPlatform();
        Supplier<ServerConfig> supplier = platform.getServerConfigSupplier();
        if (supplier != null) {
            ServerConfig serverConfig = supplier.get();
            if (serverConfig != null) {
                return serverConfig;
            }
        }
        throw new UnsupportedOperationException();
    }

    String getServerApiBaseUrl();

    void setServerApiBaseUrl(String serverApiBaseUrl);

    /** Whether this installation owns the local TuneWeave process. */
    boolean getManageTuneWeaveInternally();

    void setManageTuneWeaveInternally(boolean manageTuneWeaveInternally);

    double getPusherVoteAdditionalRate();

    void setPusherVoteAdditionalRate(double pusherVoteAdditionalRate);

    void save();

    boolean isConfigured();

    void setConfigured(boolean configured);
}
