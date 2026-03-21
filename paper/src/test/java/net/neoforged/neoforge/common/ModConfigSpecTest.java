package net.neoforged.neoforge.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModConfigSpecTest {
    @Test
    void loadFallsBackToDefaultWhenValueIsInvalid() {
        ModConfigSpec.ConfigValue<Integer> value = new ModConfigSpec.Builder()
                .defineInRange("port", Integer.valueOf(8080), Integer.valueOf(1), Integer.valueOf(65535));

        value.load("not-a-number");

        assertEquals(8080, value.get());
    }

    @Test
    void loadConvertsNumericTypesToConfiguredType() {
        ModConfigSpec.ConfigValue<Integer> value = new ModConfigSpec.Builder()
                .defineInRange("timeout", Integer.valueOf(15), Integer.valueOf(1), Integer.valueOf(60));

        value.load(20L);

        assertEquals(20, value.get());
    }
}
