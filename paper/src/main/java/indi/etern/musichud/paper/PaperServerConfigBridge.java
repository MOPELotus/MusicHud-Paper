package indi.etern.musichud.paper;

import indi.etern.musichud.server.config.ServerConfigDefinition;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

final class PaperServerConfigBridge {
    private PaperServerConfigBridge() {
    }

    static boolean apply(ConfigurationSection config) {
        boolean changed = false;
        Map<String, ModConfigSpec.ConfigValue<?>> values = ServerConfigDefinition.configure.getRight().getValues();
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : values.entrySet()) {
            changed |= syncValue(config, entry.getKey(), entry.getValue());
        }
        return changed;
    }

    private static boolean syncValue(ConfigurationSection config, String key, ModConfigSpec.ConfigValue<?> configValue) {
        if (!config.contains(key)) {
            config.set(key, configValue.getDefaultValue());
            configValue.load(configValue.getDefaultValue());
            return true;
        }
        configValue.load(config.get(key));
        return false;
    }
}
