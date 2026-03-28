package indi.etern.musichud.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-side placeholder used only so shared classes can link on Paper.
 * The real client config lives in the Fabric/NeoForge client builds.
 */
public final class ClientConfigDefinition {
    public static boolean configured = false;
    public static final Pair<ClientConfigDefinition, ModConfigSpec> configure =
            new ModConfigSpec.Builder().configure(builder -> new ClientConfigDefinition());
    public static final Value<Boolean> enable = new Value<>(false);
    public static final Value<Boolean> enableEmbeddedServer = new Value<>(false);
    public static final Value<String> clientCookie = new Value<>("");
    public static final Value<String> clientAccountConfig = new Value<>("");

    private ClientConfigDefinition() {
    }

    public static final class Value<T> {
        private T value;

        private Value(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }

        public void save() {
        }
    }
}
