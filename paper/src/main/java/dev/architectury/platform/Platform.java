package dev.architectury.platform;

import dev.architectury.utils.Env;
import net.fabricmc.api.EnvType;

public final class Platform {
    private Platform() {
    }

    public static EnvType getEnv() {
        return EnvType.SERVER;
    }

    public static Env getEnvironment() {
        return Env.SERVER;
    }
}
